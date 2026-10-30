package com.hirelink.domain.jobposting;

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
class JobPostingCloseIntegrationTest {

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
	private long owner;
	private long posting;
	private String accessToken;

	@BeforeEach
	void prepareRecruitingPosting() {
		jdbc.execute("TRUNCATE TABLE employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
		owner = createUser("EMPLOYER");
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '테스트 사업장', '테스트 대표자', 'test-documents/certificate.pdf', ?, 1,
				'테스트 주소', 37.1234567, 126.1234567, 'UNDER_5') returning id
				""", Long.class, owner, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		posting = jdbc.queryForObject("""
				insert into job_posting(workplace_id, business_type_id, other_job_name, title, job_role, wage_type,
				wage_amount, recruitment_count, work_start_date, required_korean_level, preferred_experience,
				work_time_negotiable, application_deadline_date, status, created_at, updated_at)
				values (?, 2, '기존 업종명', '테스트 채용공고', '매장 관리', 'HOURLY', 13000, 2, '2020-01-01',
				'INTERMEDIATE', 'NONE', false, '2020-01-31', 'RECRUITING', '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z') returning id
				""", Long.class, workplace);
		jdbc.update("insert into job_posting_work_time(job_posting_id, day_of_week, start_time, end_time) values (?, 'MON', '08:00', '12:00')", posting);
		accessToken = token(owner, "EMPLOYER");
	}

	@Test
	void closesPostingAndPreservesConditionsWhileReadApisReflectClosedStatus() throws Exception {
		long student = createUser("STUDENT");
		String studentToken = token(student, "STUDENT");
		String path = "/api/job-postings/" + posting;
		assertThat(send("GET", path, studentToken, null).statusCode()).isEqualTo(200);
		Map<String, List<Map<String, Object>>> before = snapshot();
		Instant started = Instant.now();
		HttpResponse<String> response = close(accessToken, Long.toString(posting));
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("jobPostingId", "status", "closedAt");
		assertThat(body.path("jobPostingId").asLong()).isEqualTo(posting);
		assertThat(body.path("status").asString()).isEqualTo("CLOSED");
		Instant closedAt = Instant.parse(body.path("closedAt").asString());
		assertThat(closedAt).isBetween(started, Instant.now());
		Instant savedClosedAt = savedClosedAt();
		assertThat(Duration.between(closedAt, savedClosedAt).abs()).isLessThan(Duration.ofMillis(1));
		Instant updatedAt = jdbc.queryForObject("select updated_at from job_posting where id = ?", OffsetDateTime.class, posting).toInstant();
		assertThat(updatedAt).isBetween(started, Instant.now());

		Map<String, List<Map<String, Object>>> after = snapshot();
		for (String table : List.of("times", "workplaces", "users")) {
			assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
		}
		Map<String, Object> oldPosting = before.get("postings").get(0);
		Map<String, Object> newPosting = after.get("postings").get(0);
		assertThat(newPosting.get("status")).isEqualTo("CLOSED");
		for (String field : oldPosting.keySet()) {
			if (!List.of("status", "closed_at", "updated_at").contains(field)) {
				assertThat(newPosting.get(field)).as(field).isEqualTo(oldPosting.get(field));
			}
		}
		HttpResponse<String> ownerDetail = send("GET", path, accessToken, null);
		assertThat(ownerDetail.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(ownerDetail.body()).path("status").asString()).isEqualTo("CLOSED");
		assertError(send("GET", path, studentToken, null), 403, "FORBIDDEN");
		JsonNode closedList = mapper.readTree(send("GET", "/api/employers/me/job-postings?status=CLOSED", accessToken, null).body());
		assertThat(closedList.path("jobPostings").size()).isEqualTo(1);
		assertThat(closedList.path("jobPostings").get(0).path("jobPostingId").asLong()).isEqualTo(posting);
		JsonNode recruitingList = mapper.readTree(send("GET", "/api/employers/me/job-postings?status=RECRUITING", accessToken, null).body());
		assertThat(recruitingList.path("jobPostings").size()).isZero();
		assertThat(snapshot()).isEqualTo(after);
	}

	@Test
	void rejectsDraftRepeatAndReopeningWithoutChangingSavedValues() throws Exception {
		jdbc.update("update job_posting set status = 'DRAFT' where id = ?", posting);
		Map<String, List<Map<String, Object>>> before = snapshot();
		assertError(close(accessToken, Long.toString(posting)), 409, "JOB_POSTING_STATE_CONFLICT");
		assertThat(snapshot()).isEqualTo(before);
		jdbc.update("update job_posting set status = 'RECRUITING' where id = ?", posting);
		assertThat(close(accessToken, Long.toString(posting)).statusCode()).isEqualTo(200);
		before = snapshot();
		assertError(close(accessToken, Long.toString(posting)), 409, "JOB_POSTING_STATE_CONFLICT");
		for (String json : List.of("{\"status\":\"RECRUITING\"}", "{\"status\":\"DRAFT\"}", "{\"wageAmount\":14000}")) {
			assertError(send("PATCH", "/api/job-postings/" + posting, accessToken, json), 409, "JOB_POSTING_STATE_CONFLICT");
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void checksAuthenticationCurrentRoleOwnershipAndIdBeforeChangingPosting() throws Exception {
		long other = createUser("EMPLOYER");
		long student = createUser("STUDENT");
		String id = Long.toString(posting);
		Map<String, List<Map<String, Object>>> before = snapshot();
		assertError(close(null, id), 401, "UNAUTHORIZED");
		assertError(close("invalid-token", id), 401, "UNAUTHORIZED");
		assertError(close(token(99999, "EMPLOYER"), id), 401, "UNAUTHORIZED");
		assertError(close(token(student, "STUDENT"), id), 403, "FORBIDDEN");
		assertError(close(token(student, "EMPLOYER"), id), 403, "FORBIDDEN");
		assertError(close(token(other, "EMPLOYER"), id), 403, "FORBIDDEN");
		assertError(close(accessToken, "99999"), 404, "JOB_POSTING_NOT_FOUND");
		for (String invalidId : List.of("invalid", "1.5", "9223372036854775808")) {
			assertError(close(accessToken, invalidId), 400, "INVALID_INPUT");
		}
		assertThat(snapshot()).isEqualTo(before);
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", owner);
		before = snapshot();
		assertError(close(accessToken, id), 403, "WITHDRAWN_ACCOUNT");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void concurrentCloseRequestsProduceOneSuccessAndOneConflict() throws Exception {
		CompletableFuture<HttpResponse<String>> first;
		CompletableFuture<HttpResponse<String>> second;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from job_posting where id = ? for update")) {
				statement.setLong(1, posting);
				statement.executeQuery().close();
			}
			try {
				HttpRequest request = request("PATCH", "/api/job-postings/" + posting + "/close", accessToken, null);
				first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
				second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%job_posting%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally {
				connection.rollback();
			}
		}
		List<HttpResponse<String>> responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
		for (HttpResponse<String> response : responses) {
			if (response.statusCode() == 200) {
				Instant closedAt = Instant.parse(mapper.readTree(response.body()).path("closedAt").asString());
				assertThat(Duration.between(closedAt, savedClosedAt()).abs()).isLessThan(Duration.ofMillis(1));
			} else {
				assertError(response, 409, "JOB_POSTING_STATE_CONFLICT");
			}
		}
		assertThat(jdbc.queryForObject("select status from job_posting where id = ?", String.class, posting)).isEqualTo("CLOSED");
	}

	@Test
	void preservesPostingAndReturnsSafeErrorWhenDatabaseUpdateFails() throws Exception {
		Map<String, List<Map<String, Object>>> before = snapshot();
		jdbc.execute("alter table job_posting add constraint test_reject_closed check (status <> 'CLOSED')");
		try {
			HttpResponse<String> response = close(accessToken, Long.toString(posting));
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(snapshot()).isEqualTo(before);
		} finally {
			jdbc.execute("alter table job_posting drop constraint test_reject_closed");
		}
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private Instant savedClosedAt() {
		return jdbc.queryForObject("select closed_at from job_posting where id = ?", OffsetDateTime.class, posting).toInstant();
	}

	private Map<String, List<Map<String, Object>>> snapshot() {
		return Map.of("postings", jdbc.queryForList("select * from job_posting order by id"),
				"times", jdbc.queryForList("select * from job_posting_work_time order by id"),
				"workplaces", jdbc.queryForList("select * from workplace order by id"),
				"users", jdbc.queryForList("select * from user_account order by id"));
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> close(String token, String id) throws Exception {
		return send("PATCH", "/api/job-postings/" + id + "/close", token, null);
	}

	private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
		return client.send(request(method, path, token, json), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String method, String path, String token, String json) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (json == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		} else {
			request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
		}
		return request.build();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message");
		assertThat(body.path("code").asString()).isEqualTo(code);
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
