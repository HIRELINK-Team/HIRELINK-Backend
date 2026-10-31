package com.hirelink.domain.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationCreateIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private DataSource dataSource;
	@Autowired
	private ObjectMapper mapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void createsWaitingApplicationWithSnapshotAndTwentyFourHourDeadline() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(3),
				LocalDate.now(KOREA_ZONE).plusDays(1));
		long student = createStudent("지원자");
		long recommendation = createRecommendation(student, posting, false);
		LocalDate desiredStartDate = LocalDate.now(KOREA_ZONE).plusDays(4);
		Instant started = Instant.now();

		HttpResponse<String> response = create(token(student, "STUDENT"), recommendation, desiredStartDate);

		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("applicationId", "recommendationId", "jobPostingId",
				"desiredStartDate", "status", "employerResponseDeadline", "appliedAt");
		assertThat(body.path("recommendationId").asLong()).isEqualTo(recommendation);
		assertThat(body.path("jobPostingId").asLong()).isEqualTo(posting);
		assertThat(body.path("desiredStartDate").asString()).isEqualTo(desiredStartDate.toString());
		assertThat(body.path("status").asString()).isEqualTo("WAITING_RESPONSE");
		Instant appliedAt = Instant.parse(body.path("appliedAt").asString());
		Instant deadline = Instant.parse(body.path("employerResponseDeadline").asString());
		assertThat(appliedAt).isBetween(started, Instant.now());
		assertThat(deadline).isEqualTo(appliedAt.plusSeconds(24 * 60 * 60));

		long applicationId = body.path("applicationId").asLong();
		assertThat(jdbc.queryForObject("select snapshot_version from application where id = ?", Short.class,
				applicationId)).isEqualTo((short)1);
		JsonNode snapshot = mapper.readTree(jdbc.queryForObject(
				"select application_snapshot::text from application where id = ?", String.class, applicationId));
		assertThat(snapshot.path("version").asInt()).isEqualTo(1);
		assertThat(snapshot.path("student").path("studentProfileId").asLong()).isEqualTo(student);
		assertThat(snapshot.path("student").path("name").asString()).isEqualTo("지원자");
		assertThat(snapshot.path("student").path("nationality").asString()).isEqualTo("VN");
		assertThat(snapshot.path("student").path("careers").get(0).path("careerName").asString()).isEqualTo("카페 근무");
		assertThat(snapshot.path("student").path("availableWorkTimes").get(0).path("dayOfWeek").asString())
				.isEqualTo("MON");
		assertThat(snapshot.path("jobPosting").path("jobPostingId").asLong()).isEqualTo(posting);
		assertThat(snapshot.path("jobPosting").path("title").asString()).isEqualTo("테스트 매장 홀서빙");
		assertThat(snapshot.path("jobPosting").path("workTimes").get(0).path("startTime").asString())
				.isEqualTo("09:00");
		assertThat(snapshot.path("recommendation").path("recommendationId").asLong()).isEqualTo(recommendation);
		assertThat(snapshot.path("recommendation").path("reason").path("summary").asString()).isEqualTo("조건 적합");
	}

	@Test
	void rejectsWrongRoleAndAnotherStudentsRecommendation() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(1),
				LocalDate.now(KOREA_ZONE));
		long owner = createStudent("추천 소유자");
		long other = createStudent("다른 학생");
		long recommendation = createRecommendation(owner, posting, false);

		assertError(create(token(employer, "EMPLOYER"), recommendation, LocalDate.now(KOREA_ZONE).plusDays(1)),
				403, "FORBIDDEN");
		assertError(create(token(other, "STUDENT"), recommendation, LocalDate.now(KOREA_ZONE).plusDays(1)),
				403, "FORBIDDEN");
		assertThat(applicationCount()).isZero();
	}

	@Test
	void validatesAuthenticationAndCurrentAccountState() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(1),
				LocalDate.now(KOREA_ZONE));
		long student = createStudent("지원자");
		long recommendation = createRecommendation(student, posting, false);
		LocalDate desiredStartDate = LocalDate.now(KOREA_ZONE).plusDays(1);

		assertError(create(null, recommendation, desiredStartDate), 401, "UNAUTHORIZED");
		assertError(create(token(99999, "STUDENT"), recommendation, desiredStartDate), 401, "UNAUTHORIZED");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", student);
		assertError(create(token(student, "STUDENT"), recommendation, desiredStartDate), 403, "WITHDRAWN_ACCOUNT");
		assertThat(applicationCount()).isZero();
	}

	@Test
	void rejectsInvalidRecommendationAndClosedOrExpiredPosting() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long student = createStudent("지원자");
		long recruiting = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(1),
				LocalDate.now(KOREA_ZONE));
		long invalidated = createRecommendation(student, recruiting, true);

		assertError(create(token(student, "STUDENT"), 99999, LocalDate.now(KOREA_ZONE).plusDays(1)),
				404, "RECOMMENDATION_NOT_FOUND");
		assertError(create(token(student, "STUDENT"), invalidated, LocalDate.now(KOREA_ZONE).plusDays(1)),
				409, "RECOMMENDATION_STATE_CONFLICT");

		long closed = createPosting(employer, "CLOSED", LocalDate.now(KOREA_ZONE).plusDays(1),
				LocalDate.now(KOREA_ZONE));
		long closedRecommendation = createRecommendation(student, closed, false);
		assertError(create(token(student, "STUDENT"), closedRecommendation, LocalDate.now(KOREA_ZONE).plusDays(1)),
				409, "JOB_POSTING_STATE_CONFLICT");

		long expired = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(1),
				LocalDate.now(KOREA_ZONE).minusDays(1));
		long expiredRecommendation = createRecommendation(student, expired, false);
		assertError(create(token(student, "STUDENT"), expiredRecommendation, LocalDate.now(KOREA_ZONE).plusDays(1)),
				409, "JOB_POSTING_STATE_CONFLICT");
		assertThat(applicationCount()).isZero();
	}

	@Test
	void rejectsPastDateAndDateBeforePostingStart() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(3),
				LocalDate.now(KOREA_ZONE));
		long student = createStudent("지원자");
		long recommendation = createRecommendation(student, posting, false);

		assertError(create(token(student, "STUDENT"), recommendation, LocalDate.now(KOREA_ZONE).minusDays(1)),
				400, "INVALID_INPUT");
		assertError(create(token(student, "STUDENT"), recommendation, LocalDate.now(KOREA_ZONE).plusDays(2)),
				400, "INVALID_INPUT");
		assertThat(applicationCount()).isZero();
	}

	@Test
	void preventsReapplicationEvenAfterPreviousApplicationEnded() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(1),
				LocalDate.now(KOREA_ZONE));
		long student = createStudent("지원자");
		long firstRecommendation = createRecommendation(student, posting, false);
		insertApplication(firstRecommendation, student, posting, "ENDED");
		long secondRecommendation = createRecommendation(student, posting, false);

		assertError(create(token(student, "STUDENT"), secondRecommendation, LocalDate.now(KOREA_ZONE).plusDays(1)),
				409, "DUPLICATE_APPLICATION");
		assertThat(applicationCount()).isEqualTo(1);
	}

	@Test
	void rejectsSixthWaitingApplicationButDoesNotCountOfferedOrEndedHistory() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(1),
				LocalDate.now(KOREA_ZONE));
		for (int index = 0; index < 5; index++) {
			long activeStudent = createStudent("활성 " + index);
			long recommendation = createRecommendation(activeStudent, posting, false);
			insertApplication(recommendation, activeStudent, posting, "WAITING_RESPONSE");
		}
		long offeredStudent = createStudent("제안 지원자");
		insertApplication(createRecommendation(offeredStudent, posting, false), offeredStudent, posting, "OFFERED");
		long endedStudent = createStudent("종료 지원자");
		insertApplication(createRecommendation(endedStudent, posting, false), endedStudent, posting, "ENDED");
		long applicant = createStudent("여섯 번째 지원자");
		long recommendation = createRecommendation(applicant, posting, false);

		assertError(create(token(applicant, "STUDENT"), recommendation, LocalDate.now(KOREA_ZONE).plusDays(1)),
				409, "APPLICATION_CAPACITY_EXCEEDED");
		assertThat(applicationCount()).isEqualTo(7);
	}

	@Test
	void concurrentApplicationsCannotExceedFiveActiveReviews() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "RECRUITING", LocalDate.now(KOREA_ZONE).plusDays(1),
				LocalDate.now(KOREA_ZONE));
		for (int index = 0; index < 4; index++) {
			long existingStudent = createStudent("기존 " + index);
			insertApplication(createRecommendation(existingStudent, posting, false), existingStudent, posting,
					"WAITING_RESPONSE");
		}
		long firstStudent = createStudent("동시 지원자 1");
		long secondStudent = createStudent("동시 지원자 2");
		long firstRecommendation = createRecommendation(firstStudent, posting, false);
		long secondRecommendation = createRecommendation(secondStudent, posting, false);
		CompletableFuture<HttpResponse<String>> first;
		CompletableFuture<HttpResponse<String>> second;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from job_posting where id = ? for update")) {
				statement.setLong(1, posting);
				statement.executeQuery().close();
			}
			try {
				first = client.sendAsync(request(token(firstStudent, "STUDENT"), firstRecommendation,
						LocalDate.now(KOREA_ZONE).plusDays(1)), HttpResponse.BodyHandlers.ofString());
				second = client.sendAsync(request(token(secondStudent, "STUDENT"), secondRecommendation,
						LocalDate.now(KOREA_ZONE).plusDays(1)), HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%job_posting%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally {
				connection.rollback();
			}
		}

		List<HttpResponse<String>> responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 409);
		responses.stream().filter(response -> response.statusCode() == 409).findFirst()
				.ifPresent(response -> assertError(response, 409, "APPLICATION_CAPACITY_EXCEEDED"));
		assertThat(jdbc.queryForObject("""
				select count(*) from application where job_posting_id = ? and status = 'WAITING_RESPONSE'
				""", Integer.class, posting)).isEqualTo(5);
	}

	private long createStudent(String name) {
		long student = createUser("STUDENT", name);
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level,
				university_name, academic_state, topik_level, stay_expiration_date)
				values (?, '2000-01-01', 'VN', '서울', 'INTERMEDIATE', '테스트대학교', 'ENROLLED', 4, '2027-12-31')
				""", student);
		jdbc.update("""
				insert into student_career(user_id, career_name, start_month, end_month, description)
				values (?, '카페 근무', '2025-01', '2025-06', '홀 업무')
				""", student);
		jdbc.update("""
				insert into student_preferred_work_time(user_id, day_of_week, start_time, end_time)
				values (?, 'MON', '10:00', '18:00')
				""", student);
		return student;
	}

	private long createUser(String role, String name) {
		return jdbc.queryForObject("insert into user_account(email, name, role) values (?, ?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", name, role);
	}

	private long createPosting(long employer, String status, LocalDate workStartDate, LocalDate applicationDeadline) {
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '테스트 매장', '테스트 대표자', 'test/certificate.pdf', ?, 1,
				'서울', 37.1234567, 127.1234567, 'UNDER_5') returning id
				""", Long.class, employer, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		long posting = jdbc.queryForObject("""
				insert into job_posting(workplace_id, business_type_id, title, job_role, wage_type, wage_amount,
				status, recruitment_count, work_start_date, required_korean_level, preferred_experience,
				work_time_negotiable, application_deadline_date)
				values (?, 1, '테스트 매장 홀서빙', '홀서빙', 'HOURLY', 12000, ?, 2, ?, 'INTERMEDIATE',
				'NONE', false, ?) returning id
				""", Long.class, workplace, status, workStartDate, applicationDeadline);
		jdbc.update("""
				insert into job_posting_work_time(job_posting_id, day_of_week, start_time, end_time)
				values (?, 'MON', '09:00', '18:00')
				""", posting);
		return posting;
	}

	private long createRecommendation(long student, long posting, boolean invalidated) {
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot,
				invalidated_at, created_at)
				values (?, 'v1', '{"region":"SEOUL"}'::jsonb, case when ? then now() else null end, now()) returning id
				""", Long.class, student, invalidated);
		return jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 90.00, '{"summary":"조건 적합"}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
	}

	private void insertApplication(long recommendation, long student, long posting, String status) {
		String endReason = status.equals("ENDED") ? "WITHDRAWN" : null;
		jdbc.update("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, end_reason, employer_response_deadline, applied_at, ended_at)
				values (?, ?, ?, current_date + 1, 1, '{}'::jsonb, ?, ?, now() + interval '24 hours', now(),
				case when ? then now() else null end)
				""", recommendation, student, posting, status, endReason, status.equals("ENDED"));
	}

	private int applicationCount() {
		return jdbc.queryForObject("select count(*) from application", Integer.class);
	}

	private HttpResponse<String> create(String token, long recommendationId, LocalDate desiredStartDate)
			throws Exception {
		return client.send(request(token, recommendationId, desiredStartDate), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String token, long recommendationId, LocalDate desiredStartDate) {
		String body = "{\"recommendationId\":" + recommendationId + ",\"desiredStartDate\":\""
				+ desiredStartDate + "\"}";
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/applications"))
				.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return request.build();
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
