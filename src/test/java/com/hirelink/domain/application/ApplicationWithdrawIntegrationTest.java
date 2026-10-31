package com.hirelink.domain.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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

import com.hirelink.domain.application.entity.ApplicationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationWithdrawIntegrationTest {

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
	void withdrawsOwnWaitingApplicationAndKeepsItsHistory() throws Exception {
		Fixture fixture = createApplication(ApplicationStatus.WAITING_RESPONSE);
		Map<String, Object> before = application(fixture.applicationId());
		Instant started = Instant.now();

		HttpResponse<String> response = withdraw(token(fixture.studentId(), "STUDENT"),
				Long.toString(fixture.applicationId()));

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("applicationId", "status", "endReason", "endedAt");
		assertThat(body.path("applicationId").asLong()).isEqualTo(fixture.applicationId());
		assertThat(body.path("status").asString()).isEqualTo("ENDED");
		assertThat(body.path("endReason").asString()).isEqualTo("WITHDRAWN");
		Instant endedAt = Instant.parse(body.path("endedAt").asString());
		assertThat(endedAt).isBetween(started, Instant.now());

		Map<String, Object> saved = application(fixture.applicationId());
		assertThat(saved).containsEntry("status", "ENDED").containsEntry("end_reason", "WITHDRAWN");
		Instant savedEndedAt = jdbc.queryForObject("select ended_at from application where id = ?",
				OffsetDateTime.class, fixture.applicationId()).toInstant();
		assertThat(savedEndedAt).isEqualTo(endedAt);
		assertThat(jdbc.queryForObject("select count(*) from application where id = ?", Integer.class,
				fixture.applicationId())).isEqualTo(1);
		for (String unchanged : List.of("recommendation_id", "student_profile_id", "job_posting_id",
				"desired_start_date", "snapshot_version", "application_snapshot", "employer_response_deadline", "applied_at")) {
			assertThat(saved.get(unchanged)).as(unchanged).isEqualTo(before.get(unchanged));
		}
	}

	@Test
	void requiresAuthentication() throws Exception {
		Fixture fixture = createApplication(ApplicationStatus.WAITING_RESPONSE);
		Map<String, Object> before = application(fixture.applicationId());

		assertError(withdraw(null, Long.toString(fixture.applicationId())), 401, "UNAUTHORIZED");
		assertError(withdraw("invalid-token", Long.toString(fixture.applicationId())), 401, "UNAUTHORIZED");
		assertThat(application(fixture.applicationId())).isEqualTo(before);
	}

	@Test
	void rejectsEmployerAccount() throws Exception {
		Fixture fixture = createApplication(ApplicationStatus.WAITING_RESPONSE);
		long employer = createUser("EMPLOYER");
		Map<String, Object> before = application(fixture.applicationId());

		assertError(withdraw(token(employer, "EMPLOYER"), Long.toString(fixture.applicationId())),
				403, "FORBIDDEN");
		assertThat(application(fixture.applicationId())).isEqualTo(before);
	}

	@Test
	void rejectsAnotherStudentApplication() throws Exception {
		Fixture fixture = createApplication(ApplicationStatus.WAITING_RESPONSE);
		long otherStudent = createStudent();
		Map<String, Object> before = application(fixture.applicationId());

		assertError(withdraw(token(otherStudent, "STUDENT"), Long.toString(fixture.applicationId())),
				403, "FORBIDDEN");
		assertThat(application(fixture.applicationId())).isEqualTo(before);
	}

	@Test
	void returnsNotFoundForMissingApplication() throws Exception {
		long student = createStudent();

		assertError(withdraw(token(student, "STUDENT"), "99999"), 404, "APPLICATION_NOT_FOUND");
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = {"OFFERED", "COMPLETED", "ENDED"})
	void rejectsApplicationsOutsideWaitingResponse(ApplicationStatus status) throws Exception {
		Fixture fixture = createApplication(status);
		Map<String, Object> before = application(fixture.applicationId());

		assertError(withdraw(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.applicationId())),
				409, "APPLICATION_STATE_CONFLICT");
		assertThat(application(fixture.applicationId())).isEqualTo(before);
	}

	@Test
	void rejectsRepeatedWithdrawalWithoutChangingTheFirstResult() throws Exception {
		Fixture fixture = createApplication(ApplicationStatus.WAITING_RESPONSE);
		String accessToken = token(fixture.studentId(), "STUDENT");
		assertThat(withdraw(accessToken, Long.toString(fixture.applicationId())).statusCode()).isEqualTo(200);
		Map<String, Object> withdrawn = application(fixture.applicationId());

		assertError(withdraw(accessToken, Long.toString(fixture.applicationId())),
				409, "APPLICATION_STATE_CONFLICT");
		assertThat(application(fixture.applicationId())).isEqualTo(withdrawn);
	}

	@ParameterizedTest
	@ValueSource(strings = {"invalid", "1.5", "9223372036854775808"})
	void rejectsInvalidApplicationIdFormat(String id) throws Exception {
		long student = createStudent();

		assertError(withdraw(token(student, "STUDENT"), id), 400, "INVALID_INPUT");
	}

	@Test
	void validatesCurrentAccountExistenceAndWithdrawalState() throws Exception {
		Fixture fixture = createApplication(ApplicationStatus.WAITING_RESPONSE);
		Map<String, Object> before = application(fixture.applicationId());

		assertError(withdraw(token(99999, "STUDENT"), Long.toString(fixture.applicationId())),
				401, "UNAUTHORIZED");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", fixture.studentId());
		assertError(withdraw(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.applicationId())),
				403, "WITHDRAWN_ACCOUNT");
		assertThat(application(fixture.applicationId())).isEqualTo(before);
	}

	@Test
	void concurrentWithdrawalsProduceOneSuccessAndOneConflict() throws Exception {
		Fixture fixture = createApplication(ApplicationStatus.WAITING_RESPONSE);
		String accessToken = token(fixture.studentId(), "STUDENT");
		CompletableFuture<HttpResponse<String>> first;
		CompletableFuture<HttpResponse<String>> second;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from application where id = ? for update")) {
				statement.setLong(1, fixture.applicationId());
				statement.executeQuery().close();
			}
			try {
				HttpRequest request = request(accessToken, Long.toString(fixture.applicationId()));
				first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
				second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%application%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally {
				connection.rollback();
			}
		}

		List<HttpResponse<String>> responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
		responses.stream().filter(response -> response.statusCode() == 409).findFirst()
				.ifPresent(response -> assertError(response, 409, "APPLICATION_STATE_CONFLICT"));
		assertThat(application(fixture.applicationId()))
				.containsEntry("status", "ENDED").containsEntry("end_reason", "WITHDRAWN");
	}

	private Fixture createApplication(ApplicationStatus status) {
		long student = createStudent();
		long employer = createUser("EMPLOYER");
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '테스트 사업장', '테스트 대표자', 'test/certificate.pdf', ?, 1,
				'서울', 37.1234567, 127.1234567, 'UNDER_5') returning id
				""", Long.class, employer, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		long posting = jdbc.queryForObject(
				"insert into job_posting(workplace_id, status) values (?, 'RECRUITING') returning id",
				Long.class, workplace);
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 90.00, '{}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
		String endReason = status == ApplicationStatus.ENDED ? "WITHDRAWN" : null;
		Long application = jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, end_reason, employer_response_deadline, applied_at, ended_at)
				values (?, ?, ?, current_date, 1, '{}'::jsonb, ?, ?, now() + interval '24 hours', now(),
				case when ? then now() else null end) returning id
				""", Long.class, recommendation, student, posting, status.name(), endReason,
				status == ApplicationStatus.ENDED);
		return new Fixture(student, application);
	}

	private long createStudent() {
		long student = createUser("STUDENT");
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-01-01', 'VN', '서울', 'INTERMEDIATE')
				""", student);
		return student;
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private Map<String, Object> application(long applicationId) {
		return jdbc.queryForMap("select * from application where id = ?", applicationId);
	}

	private HttpResponse<String> withdraw(String token, String id) throws Exception {
		return client.send(request(token, id), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String token, String id) {
		HttpRequest.Builder request = HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + "/api/applications/" + id + "/withdraw"))
				.timeout(Duration.ofSeconds(15)).method("PATCH", HttpRequest.BodyPublishers.noBody());
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

	private record Fixture(long studentId, long applicationId) {
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
