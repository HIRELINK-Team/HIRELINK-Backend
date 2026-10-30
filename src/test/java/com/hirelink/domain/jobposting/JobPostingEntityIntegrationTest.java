package com.hirelink.domain.jobposting;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
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

import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.entity.JobPostingStatus;
import com.hirelink.domain.jobposting.entity.JobPostingWorkTime;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.workplace.entity.BusinessType;
import com.hirelink.domain.workplace.entity.Workplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class JobPostingEntityIntegrationTest {

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
	private TransactionTemplate transactions;

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, business_type, student_career, student_profile, "
				+ "user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
		transactions = new TransactionTemplate(transactionManager);
	}

	@Test
	void persistsDraftAndFindsItsOwnerThroughWorkplace() {
		Fixture fixture = createFixture();

		transactions.executeWithoutResult(status -> {
			JobPosting posting = entityManager.find(JobPosting.class, fixture.jobPostingId());
			Workplace workplace = posting.getWorkplace();
			assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.DRAFT);
			assertThat(workplace.getId()).isEqualTo(fixture.workplaceId());
			assertThat(workplace.getEmployerUser().getId()).isEqualTo(fixture.employerUserId());
			assertThat(workplace.getEmployerUser().getRole()).isEqualTo(MemberRole.EMPLOYER);
			assertThat(workplace.getBusinessType().getId()).isEqualTo(fixture.businessTypeId());
			assertThat(workplace.getBusinessType().getName()).isEqualTo("TEST_BUSINESS");
			assertThat(workplace.getWorkplaceName()).isEqualTo("테스트 사업장");
			assertThat(workplace.getRepresentativeName()).isEqualTo("테스트 대표");
			assertThat(workplace.getBusinessRegistrationFileKey()).isEqualTo("test/business-registration.pdf");
			assertThat(workplace.getBusinessRegistrationNumber()).isEqualTo("TEST-REGISTRATION");
			assertThat(workplace.getWorkplaceAddress()).isEqualTo("테스트 주소");
			assertThat(workplace.getLatitude()).isEqualByComparingTo("37.1234567");
			assertThat(workplace.getLongitude()).isEqualByComparingTo("127.1234567");
			assertThat(workplace.getEmployeeCountRange()).isEqualTo("TEST_RANGE");
			assertThat(posting.getCreatedAt()).isNotNull();
			assertThat(posting.getUpdatedAt()).isEqualTo(posting.getCreatedAt());
		});

		assertThat(jdbc.queryForMap("select * from job_posting where id = ?", fixture.jobPostingId()))
				.containsEntry("status", "DRAFT")
				.containsEntry("business_type_id", null)
				.containsEntry("other_job_name", null)
				.containsEntry("title", null)
				.containsEntry("job_role", null)
				.containsEntry("wage_type", null)
				.containsEntry("wage_amount", null)
				.containsEntry("recruitment_count", null)
				.containsEntry("work_start_date", null)
				.containsEntry("required_korean_level", null)
				.containsEntry("preferred_experience", null)
				.containsEntry("work_time_negotiable", null)
				.containsEntry("application_deadline_date", null)
				.containsEntry("closed_at", null);
	}

	@Test
	void readsAllPostingColumnsAndKeepsWorkplaceAndPostingBusinessTypesSeparate() {
		Fixture fixture = createFixture();
		Long otherBusinessTypeId = jdbc.queryForObject(
				"insert into business_type (name) values ('TEST_OTHER_BUSINESS') returning id", Long.class);
		Instant closedAt = Instant.parse("2026-10-29T12:30:00Z");
		jdbc.update("""
				update job_posting set business_type_id = ?, other_job_name = ?, title = ?, job_role = ?,
				wage_type = 'HOURLY', wage_amount = 13000, status = 'CLOSED', recruitment_count = 2,
				work_start_date = '2026-10-01', required_korean_level = 'INTERMEDIATE',
				preferred_experience = 'NONE', work_time_negotiable = false,
				application_deadline_date = '2026-10-28', closed_at = ? where id = ?
				""", otherBusinessTypeId, "테스트 기타 직무", "테스트 공고", "테스트 직무",
				Timestamp.from(closedAt), fixture.jobPostingId());

		transactions.executeWithoutResult(status -> {
			JobPosting posting = entityManager.find(JobPosting.class, fixture.jobPostingId());
			assertThat(posting.getBusinessType().getId()).isEqualTo(otherBusinessTypeId);
			assertThat(posting.getWorkplace().getBusinessType().getId()).isEqualTo(fixture.businessTypeId());
			assertThat(posting.getOtherJobName()).isEqualTo("테스트 기타 직무");
			assertThat(posting.getTitle()).isEqualTo("테스트 공고");
			assertThat(posting.getJobRole()).isEqualTo("테스트 직무");
			assertThat(posting.getWageType()).isEqualTo("HOURLY");
			assertThat(posting.getWageAmount()).isEqualTo(13000);
			assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.CLOSED);
			assertThat(posting.getRecruitmentCount()).isEqualTo((short) 2);
			assertThat(posting.getWorkStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
			assertThat(posting.getRequiredKoreanLevel()).isEqualTo("INTERMEDIATE");
			assertThat(posting.getPreferredExperience()).isEqualTo("NONE");
			assertThat(posting.getWorkTimeNegotiable()).isFalse();
			assertThat(posting.getApplicationDeadlineDate()).isEqualTo(LocalDate.of(2026, 10, 28));
			assertThat(posting.getClosedAt()).isEqualTo(closedAt);
		});
	}

	@Test
	void enforcesUniqueKeysForeignKeysAndRequiredStatusInDatabase() {
		Fixture fixture = createFixture();
		assertThatThrownBy(() -> jdbc.update("insert into business_type (name) values ('TEST_BUSINESS')"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				insert into workplace (employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				select employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range from workplace where id = ?
				""", fixture.workplaceId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update workplace set employer_user_id = -1 where id = ?", fixture.workplaceId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update workplace set business_type_id = -1 where id = ?", fixture.workplaceId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update job_posting set workplace_id = -1 where id = ?", fixture.jobPostingId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("update job_posting set business_type_id = -1 where id = ?", fixture.jobPostingId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("insert into job_posting (workplace_id) values (?)", fixture.workplaceId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("delete from workplace where id = ?", fixture.workplaceId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("delete from user_account where id = ?", fixture.employerUserId()))
				.isInstanceOf(DataIntegrityViolationException.class);

		Long postingId = jdbc.queryForObject(
				"insert into job_posting (workplace_id, status) values (?, 'RECRUITING') returning id",
				Long.class, fixture.workplaceId());
		transactions.executeWithoutResult(status -> {
			JobPosting posting = entityManager.find(JobPosting.class, postingId);
			assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.RECRUITING);
			assertThat(posting.getCreatedAt()).isNotNull();
			assertThat(posting.getUpdatedAt()).isNotNull();
		});
	}

	@Test
	void persistsWorkTimeAndEnforcesItsForeignKeyAndRequiredColumns() {
		Fixture fixture = createFixture();
		Long workTimeId = transactions.execute(status -> {
			JobPosting posting = entityManager.find(JobPosting.class, fixture.jobPostingId());
			JobPostingWorkTime time = new JobPostingWorkTime(posting, "MON", LocalTime.of(22, 0), LocalTime.MIDNIGHT);
			entityManager.persist(time);
			entityManager.flush();
			return time.getId();
		});
		transactions.executeWithoutResult(status -> {
			JobPostingWorkTime time = entityManager.find(JobPostingWorkTime.class, workTimeId);
			assertThat(time.getJobPosting().getId()).isEqualTo(fixture.jobPostingId());
			assertThat(time.getDayOfWeek()).isEqualTo("MON");
			assertThat(time.getStartTime()).isEqualTo(LocalTime.of(22, 0));
			assertThat(time.getEndTime()).isEqualTo(LocalTime.MIDNIGHT);
		});
		assertThatThrownBy(() -> jdbc.update("""
				insert into job_posting_work_time(job_posting_id, day_of_week, start_time, end_time)
				values (-1, 'MON', '09:00', '18:00')
				""")).isInstanceOf(DataIntegrityViolationException.class);
		for (String column : new String[] {"job_posting_id", "day_of_week", "start_time", "end_time"}) {
			assertThatThrownBy(() -> jdbc.update("update job_posting_work_time set " + column + " = null where id = ?", workTimeId))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
		assertThat(jdbc.queryForObject("select count(*) from job_posting_work_time", Integer.class)).isEqualTo(1);
	}

	private Fixture createFixture() {
		return transactions.execute(status -> {
			UserAccount employer = new UserAccount(UUID.randomUUID() + "@example.com", MemberRole.EMPLOYER);
			BusinessType businessType = new BusinessType("TEST_BUSINESS");
			entityManager.persist(employer);
			entityManager.persist(businessType);
			Workplace workplace = new Workplace(employer, "테스트 사업장", "테스트 대표",
					"test/business-registration.pdf", "TEST-REGISTRATION", businessType, "테스트 주소",
					new BigDecimal("37.1234567"), new BigDecimal("127.1234567"), "TEST_RANGE");
			entityManager.persist(workplace);
			JobPosting posting = new JobPosting(workplace, JobPostingStatus.DRAFT);
			entityManager.persist(posting);
			return new Fixture(employer.getId(), businessType.getId(), workplace.getId(), posting.getId());
		});
	}

	private record Fixture(Long employerUserId, Long businessTypeId, Long workplaceId, Long jobPostingId) {
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
