package com.hirelink.domain.application;

import com.hirelink.support.PostgreSQLIntegrationTest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hirelink.domain.application.service.ApplicationExpirationScheduler;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(ApplicationExpirationIntegrationTest.TestClockConfiguration.class)
class ApplicationExpirationIntegrationTest extends PostgreSQLIntegrationTest {

	private static final Instant PROCESSING_TIME = Instant.parse("2026-11-06T03:00:00.123456Z");
	private static final String TEST_JWT_SECRET = Base64.getEncoder().encodeToString(new byte[32]);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ApplicationExpirationScheduler scheduler;

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void expiresOverdueResponsesAndKeepsFutureOrAlreadyEndedRowsUnchanged() {
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer);
		long employerExpired = createApplication(createStudent(), posting, "WAITING_RESPONSE",
				PROCESSING_TIME.minusSeconds(1), null, null);
		long employerFuture = createApplication(createStudent(), posting, "WAITING_RESPONSE",
				PROCESSING_TIME.plusSeconds(1), null, null);
		long studentExpired = createApplication(createStudent(), posting, "OFFERED",
				PROCESSING_TIME.minusSeconds(100), null, null);
		long expiredOffer = createOffer(studentExpired, PROCESSING_TIME.minusSeconds(1));
		long studentFuture = createApplication(createStudent(), posting, "OFFERED",
				PROCESSING_TIME.minusSeconds(100), null, null);
		long futureOffer = createOffer(studentFuture, PROCESSING_TIME.plusSeconds(1));
		Instant originalEndedAt = PROCESSING_TIME.minusSeconds(500);
		long alreadyEnded = createApplication(createStudent(), posting, "ENDED",
				PROCESSING_TIME.minusSeconds(1000), "WITHDRAWN", originalEndedAt);

		scheduler.expireResponses();
		scheduler.expireResponses();

		assertApplication(employerExpired, "ENDED", "EMPLOYER_RESPONSE_EXPIRED", PROCESSING_TIME);
		assertApplication(studentExpired, "ENDED", "STUDENT_RESPONSE_EXPIRED", PROCESSING_TIME);
		assertOffer(expiredOffer, "EXPIRED", "STUDENT_RESPONSE_EXPIRED", PROCESSING_TIME);
		assertApplication(employerFuture, "WAITING_RESPONSE", null, null);
		assertApplication(studentFuture, "OFFERED", null, null);
		assertOffer(futureOffer, "PENDING", null, null);
		assertApplication(alreadyEnded, "ENDED", "WITHDRAWN", originalEndedAt);
		assertThat(jdbc.queryForObject("select count(*) from application", Integer.class)).isEqualTo(5);
		assertThat(jdbc.queryForObject("""
				select count(*) from application where job_posting_id = ?
				and status in ('WAITING_RESPONSE', 'OFFERED')
				""", Integer.class, posting)).isEqualTo(2);
	}

	private long createApplication(long student, long posting, String status, Instant deadline,
			String endReason, Instant endedAt) {
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 90.00, '{}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
		return jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, end_reason, employer_response_deadline, applied_at, ended_at)
				values (?, ?, ?, current_date, 1, '{}'::jsonb, ?, ?, ?, now(), ?) returning id
				""", Long.class, recommendation, student, posting, status, endReason,
				OffsetDateTime.ofInstant(deadline, ZoneOffset.UTC),
				endedAt == null ? null : OffsetDateTime.ofInstant(endedAt, ZoneOffset.UTC));
	}

	private long createOffer(long applicationId, Instant deadline) {
		return jdbc.queryForObject("""
				insert into employment_offer(application_id, status, student_response_deadline, offered_at)
				values (?, 'PENDING', ?, now()) returning id
				""", Long.class, applicationId, OffsetDateTime.ofInstant(deadline, ZoneOffset.UTC));
	}

	private long createStudent() {
		long student = createUser("STUDENT");
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-01-01', 'VN', '서울', 'INTERMEDIATE')
				""", student);
		return student;
	}

	private long createPosting(long employer) {
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '테스트 사업장', '테스트 대표자', 'test/certificate.pdf', ?, 1,
				'서울', 37.1234567, 127.1234567, 'UNDER_5') returning id
				""", Long.class, employer, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		return jdbc.queryForObject(
				"insert into job_posting(workplace_id, status) values (?, 'RECRUITING') returning id",
				Long.class, workplace);
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private void assertApplication(long id, String status, String endReason, Instant endedAt) {
		Map<String, Object> application = jdbc.queryForMap(
				"select status, end_reason, ended_at from application where id = ?", id);
		assertThat(application.get("status")).isEqualTo(status);
		assertThat(application.get("end_reason")).isEqualTo(endReason);
		assertThat(toInstant(application.get("ended_at"))).isEqualTo(endedAt);
	}

	private void assertOffer(long id, String status, String endReason, Instant endedAt) {
		Map<String, Object> offer = jdbc.queryForMap(
				"select status, end_reason, ended_at from employment_offer where id = ?", id);
		assertThat(offer.get("status")).isEqualTo(status);
		assertThat(offer.get("end_reason")).isEqualTo(endReason);
		assertThat(toInstant(offer.get("ended_at"))).isEqualTo(endedAt);
	}

	private Instant toInstant(Object value) {
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime.toInstant();
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant();
		}
		return (Instant)value;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(PROCESSING_TIME, ZoneOffset.UTC);
		}
	}
}
