package com.hirelink.domain.matching;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.entity.ApplicationEndReason;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.employmentmatch.entity.EmploymentMatch;
import com.hirelink.domain.employmentmatch.repository.EmploymentMatchRepository;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferEndReason;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;
import com.hirelink.domain.employmentoffer.repository.EmploymentOfferRepository;
import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.entity.JobPostingStatus;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.recommendation.entity.Recommendation;
import com.hirelink.domain.recommendation.entity.RecommendationBatch;
import com.hirelink.domain.recommendation.repository.RecommendationBatchRepository;
import com.hirelink.domain.recommendation.repository.RecommendationRepository;
import com.hirelink.domain.student.entity.KoreanLevel;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.workplace.entity.BusinessType;
import com.hirelink.domain.workplace.entity.Workplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class MatchingDomainEntityIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@Autowired
	private EntityManager entityManager;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private RecommendationBatchRepository recommendationBatchRepository;
	@Autowired
	private RecommendationRepository recommendationRepository;
	@Autowired
	private ApplicationRepository applicationRepository;
	@Autowired
	private EmploymentOfferRepository employmentOfferRepository;
	@Autowired
	private EmploymentMatchRepository employmentMatchRepository;

	private TransactionTemplate transactions;

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, business_type, "
				+ "student_career, student_profile, user_term_agreement, "
				+ "phone_verification, phone_verification_send_attempt, refresh_token, auth_account, user_account "
				+ "RESTART IDENTITY");
		transactions = new TransactionTemplate(transactionManager);
	}

	@Test
	void persistsAndReadsTheCompleteMatchingRelationshipChain() {
		Fixture fixture = createCompleteFixture();

		transactions.executeWithoutResult(status -> {
			RecommendationBatch batch = recommendationBatchRepository.findById(fixture.batchId()).orElseThrow();
			Recommendation recommendation = recommendationRepository.findById(fixture.recommendationId()).orElseThrow();
			Application application = applicationRepository.findById(fixture.applicationId()).orElseThrow();
			EmploymentOffer offer = employmentOfferRepository.findById(fixture.offerId()).orElseThrow();
			EmploymentMatch match = employmentMatchRepository.findById(fixture.matchId()).orElseThrow();

			assertThat(batch.getStudentProfile().getId()).isEqualTo(fixture.studentProfileId());
			assertThat(batch.getAlgorithmVersion()).isEqualTo("v1");
			assertThat(batch.getInputSnapshot()).containsEntry("region", "SEOUL");
			assertThat(batch.getInvalidatedAt()).isNull();
			assertThat(recommendation.getBatch().getId()).isEqualTo(batch.getId());
			assertThat(recommendation.getJobPosting().getId()).isEqualTo(fixture.jobPostingId());
			assertThat(recommendation.getRank()).isEqualTo((short)1);
			assertThat(recommendation.getScore()).isEqualByComparingTo("87.50");
			assertThat(recommendation.getReasonJson()).containsEntry("summary", "조건 적합");
			assertThat(application.getRecommendation().getId()).isEqualTo(recommendation.getId());
			assertThat(application.getStudentProfile().getId()).isEqualTo(fixture.studentProfileId());
			assertThat(application.getJobPosting().getId()).isEqualTo(fixture.jobPostingId());
			assertThat(application.getStatus()).isEqualTo(ApplicationStatus.COMPLETED);
			assertThat(application.getApplicationSnapshot()).containsEntry("version", "test");
			assertThat(offer.getApplication().getId()).isEqualTo(application.getId());
			assertThat(offer.getStatus()).isEqualTo(EmploymentOfferStatus.ACCEPTED);
			assertThat(match.getEmploymentOffer().getId()).isEqualTo(offer.getId());
		});

		assertThat(jdbc.queryForObject(
				"select input_snapshot ->> 'region' from recommendation_batch where id = ?",
				String.class, fixture.batchId())).isEqualTo("SEOUL");
		assertThat(jdbc.queryForObject(
				"select application_snapshot ->> 'version' from application where id = ?",
				String.class, fixture.applicationId())).isEqualTo("test");
	}

	@Test
	void enforcesRecommendationUniqueKeysRankCheckAndForeignKeys() {
		Fixture fixture = createCompleteFixture();
		Long otherPostingId = createOtherPosting(fixture.workplaceId());

		assertThatThrownBy(() -> jdbc.update("""
				insert into recommendation (batch_id, job_posting_id, rank, score, created_at)
				values (?, ?, 1, 70.00, now())
				""", fixture.batchId(), otherPostingId)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				insert into recommendation (batch_id, job_posting_id, rank, score, created_at)
				values (?, ?, 2, 70.00, now())
				""", fixture.batchId(), fixture.jobPostingId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				insert into recommendation (batch_id, job_posting_id, rank, score, created_at)
				values (?, ?, 0, 70.00, now())
				""", fixture.batchId(), otherPostingId)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				insert into recommendation_batch
				(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (-1, 'v1', '{}'::jsonb, now())
				""")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update recommendation set job_posting_id = -1 where id = ?",
				fixture.recommendationId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update recommendation_batch set input_snapshot = null where id = ?",
				fixture.batchId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void enforcesApplicationOfferAndMatchUniqueKeysAndForeignKeys() {
		Fixture fixture = createCompleteFixture();
		Long otherBatchId = jdbc.queryForObject("""
				insert into recommendation_batch
				(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, fixture.studentProfileId());
		Long otherRecommendationId = jdbc.queryForObject("""
				insert into recommendation (batch_id, job_posting_id, rank, score, created_at)
				values (?, ?, 1, 70.00, now()) returning id
				""", Long.class, otherBatchId, fixture.jobPostingId());

		assertThatThrownBy(() -> jdbc.update("""
				insert into application
				(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				 snapshot_version, application_snapshot, status, employer_response_deadline, applied_at)
				values (?, ?, ?, current_date, 1, '{}'::jsonb, 'WAITING_RESPONSE', now(), now())
				""", fixture.recommendationId(), fixture.studentProfileId(), fixture.jobPostingId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				insert into application
				(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				 snapshot_version, application_snapshot, status, employer_response_deadline, applied_at)
				values (?, ?, ?, current_date, 1, '{}'::jsonb, 'WAITING_RESPONSE', now(), now())
				""", otherRecommendationId, fixture.studentProfileId(), fixture.jobPostingId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				insert into employment_offer
				(application_id, status, student_response_deadline, offered_at)
				values (?, 'PENDING', now(), now())
				""", fixture.applicationId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				insert into employment_match (employment_offer_id, completed_at) values (?, now())
				""", fixture.offerId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update application set status = 'INVALID' where id = ?",
				fixture.applicationId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update employment_offer set end_reason = 'INVALID' where id = ?",
				fixture.offerId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update application set student_profile_id = -1 where id = ?",
				fixture.applicationId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update employment_offer set application_id = -1 where id = ?",
				fixture.offerId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update employment_match set employment_offer_id = -1 where id = ?",
				fixture.matchId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update employment_match set completed_at = null where id = ?",
				fixture.matchId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void keepsEntityEnumsAlignedWithDatabaseCheckValues() {
		Fixture fixture = createCompleteFixture();

		assertThat(ApplicationStatus.values()).containsExactly(
				ApplicationStatus.WAITING_RESPONSE,
				ApplicationStatus.OFFERED,
				ApplicationStatus.COMPLETED,
				ApplicationStatus.ENDED);
		assertThat(ApplicationEndReason.values()).containsExactly(
				ApplicationEndReason.EMPLOYER_REJECTED,
				ApplicationEndReason.WITHDRAWN,
				ApplicationEndReason.EMPLOYER_RESPONSE_EXPIRED,
				ApplicationEndReason.STUDENT_REJECTED,
				ApplicationEndReason.STUDENT_RESPONSE_EXPIRED,
				ApplicationEndReason.JOB_POSTING_CLOSED);
		assertThat(EmploymentOfferStatus.values()).containsExactly(
				EmploymentOfferStatus.PENDING,
				EmploymentOfferStatus.ACCEPTED,
				EmploymentOfferStatus.REJECTED,
				EmploymentOfferStatus.EXPIRED);
		assertThat(EmploymentOfferEndReason.values()).containsExactly(
				EmploymentOfferEndReason.STUDENT_REJECTED,
				EmploymentOfferEndReason.STUDENT_RESPONSE_EXPIRED,
				EmploymentOfferEndReason.JOB_POSTING_CLOSED);

		for (ApplicationStatus status : ApplicationStatus.values()) {
			assertThat(jdbc.update("update application set status = ? where id = ?", status.name(), fixture.applicationId()))
					.isEqualTo(1);
		}
		for (ApplicationEndReason endReason : ApplicationEndReason.values()) {
			assertThat(jdbc.update("update application set end_reason = ? where id = ?", endReason.name(),
					fixture.applicationId())).isEqualTo(1);
		}
		for (EmploymentOfferStatus status : EmploymentOfferStatus.values()) {
			assertThat(jdbc.update("update employment_offer set status = ? where id = ?", status.name(), fixture.offerId()))
					.isEqualTo(1);
		}
		for (EmploymentOfferEndReason endReason : EmploymentOfferEndReason.values()) {
			assertThat(jdbc.update("update employment_offer set end_reason = ? where id = ?", endReason.name(),
					fixture.offerId())).isEqualTo(1);
		}

		assertThatThrownBy(() -> jdbc.update("update application set status = 'UNDER_REVIEW' where id = ?",
				fixture.applicationId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update application set status = 'OFFER_PENDING' where id = ?",
				fixture.applicationId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update application set end_reason = 'STUDENT_WITHDREW' where id = ?",
				fixture.applicationId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update application set end_reason = 'OFFER_REJECTED' where id = ?",
				fixture.applicationId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update employment_offer set status = 'ENDED' where id = ?",
				fixture.offerId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	private Fixture createCompleteFixture() {
		return transactions.execute(status -> {
			UserAccount student = new UserAccount(UUID.randomUUID() + "@example.com", MemberRole.STUDENT);
			UserAccount employer = new UserAccount(UUID.randomUUID() + "@example.com", MemberRole.EMPLOYER);
			BusinessType businessType = new BusinessType("TEST_BUSINESS");
			entityManager.persist(student);
			entityManager.persist(employer);
			entityManager.persist(businessType);

			StudentProfile studentProfile = new StudentProfile(student, LocalDate.of(2000, 1, 1), "VN",
					"서울", KoreanLevel.INTERMEDIATE);
			entityManager.persist(studentProfile);
			Workplace workplace = new Workplace(employer, "테스트 사업장", "테스트 대표",
					"test/business-registration.pdf", "TEST-" + UUID.randomUUID().toString().substring(0, 8), businessType, "서울",
					new BigDecimal("37.1234567"), new BigDecimal("127.1234567"), "TEST_RANGE");
			entityManager.persist(workplace);
			JobPosting jobPosting = new JobPosting(workplace, JobPostingStatus.RECRUITING);
			entityManager.persist(jobPosting);

			Instant appliedAt = Instant.parse("2026-10-30T01:00:00Z");
			RecommendationBatch batch = recommendationBatchRepository.saveAndFlush(new RecommendationBatch(
					studentProfile, "v1", Map.of("region", "SEOUL"), null, appliedAt.minusSeconds(60)));
			Recommendation recommendation = recommendationRepository.saveAndFlush(new Recommendation(batch,
					jobPosting, (short)1, new BigDecimal("87.50"), Map.of("summary", "조건 적합"), appliedAt));
			Application application = applicationRepository.saveAndFlush(new Application(recommendation,
					studentProfile, jobPosting, LocalDate.of(2026, 11, 1), (short)1, Map.of("version", "test"),
					ApplicationStatus.COMPLETED, null, appliedAt.plusSeconds(86400), appliedAt, null));
			EmploymentOffer offer = employmentOfferRepository.saveAndFlush(new EmploymentOffer(application,
					EmploymentOfferStatus.ACCEPTED, null, appliedAt.plusSeconds(172800), appliedAt.plusSeconds(86400),
					appliedAt.plusSeconds(90000), null));
			EmploymentMatch match = employmentMatchRepository.saveAndFlush(
					new EmploymentMatch(offer, appliedAt.plusSeconds(90000)));

			return new Fixture(studentProfile.getId(), workplace.getId(), jobPosting.getId(), batch.getId(),
					recommendation.getId(), application.getId(), offer.getId(), match.getId());
		});
	}

	private Long createOtherPosting(Long workplaceId) {
		return jdbc.queryForObject(
				"insert into job_posting (workplace_id, status) values (?, 'RECRUITING') returning id",
				Long.class, workplaceId);
	}

	private record Fixture(Long studentProfileId, Long workplaceId, Long jobPostingId, Long batchId,
			Long recommendationId, Long applicationId, Long offerId, Long matchId) {
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
