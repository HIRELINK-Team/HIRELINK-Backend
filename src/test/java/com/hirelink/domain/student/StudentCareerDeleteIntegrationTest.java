package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
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
class StudentCareerDeleteIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final String CAREER_PATH = "/api/students/me/careers/";
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
		jdbc.execute("TRUNCATE TABLE student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting, workplace, student_career, student_profile, user_term_agreement, "
				+ "phone_verification, phone_verification_send_attempt, refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void deletesOnlySelectedCareerAndRemovesItFromProfile() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		long remainingCareerId = createCareer(userId);
		long otherCareerId = createCareer(createUser("STUDENT", true));
		Map<String, Object> remainingBefore = career(remainingCareerId);
		Map<String, Object> otherBefore = career(otherCareerId);
		Map<String, Object> userBefore = jdbc.queryForMap("select * from user_account where id = ?", userId);
		Map<String, Object> profileBefore = jdbc.queryForMap("select * from student_profile where id = ?", userId);
		String token = token(userId, "STUDENT");

		HttpResponse<String> response = delete(careerId, token);
		assertThat(response.statusCode()).isEqualTo(204);
		assertThat(response.body()).isEmpty();
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(jdbc.queryForObject("select count(*) from student_career where id = ?", Integer.class, careerId)).isZero();
		assertThat(career(remainingCareerId)).isEqualTo(remainingBefore);
		assertThat(career(otherCareerId)).isEqualTo(otherBefore);
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(userBefore);
		assertThat(jdbc.queryForMap("select * from student_profile where id = ?", userId)).isEqualTo(profileBefore);

		HttpResponse<String> read = send("GET", "/api/students/me/profile", token);
		assertThat(read.statusCode()).isEqualTo(200);
		JsonNode careers = mapper.readTree(read.body()).path("careers");
		assertThat(careers.size()).isEqualTo(1);
		assertThat(careers.get(0).path("careerId").asLong()).isEqualTo(remainingCareerId);
	}

	@Test
	void returnsNotFoundForMissingOrAlreadyDeletedCareer() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		String token = token(userId, "STUDENT");
		assertThat(delete(careerId, token).statusCode()).isEqualTo(204);
		assertError(delete(careerId, token), 404, "STUDENT_CAREER_NOT_FOUND");
		assertError(delete(99999, token), 404, "STUDENT_CAREER_NOT_FOUND");
		assertError(delete(-1, token), 404, "STUDENT_CAREER_NOT_FOUND");
		assertThat(jdbc.queryForObject("select count(*) from student_career", Integer.class)).isZero();
	}

	@Test
	void requiresAuthenticationCurrentStudentRoleAndOwnership() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		Map<String, Object> before = career(careerId);
		assertError(delete(careerId, null), 401, "UNAUTHORIZED");
		assertError(delete(careerId, "invalid-token"), 401, "UNAUTHORIZED");
		assertError(delete(careerId, token(99999, "STUDENT")), 401, "UNAUTHORIZED");
		long employerId = createUser("EMPLOYER", false);
		assertError(delete(careerId, token(employerId, "EMPLOYER")), 403, "FORBIDDEN");
		assertError(delete(careerId, token(employerId, "STUDENT")), 403, "FORBIDDEN");
		long otherUserId = createUser("STUDENT", true);
		assertError(send("DELETE", CAREER_PATH + careerId + "?userId=" + userId,
				token(otherUserId, "STUDENT")), 403, "FORBIDDEN");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", userId);
		assertError(delete(careerId, token(userId, "STUDENT")), 403, "WITHDRAWN_ACCOUNT");
		assertThat(career(careerId)).isEqualTo(before);
	}

	@Test
	void rejectsInvalidIdFormatWithoutDeletingData() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		Map<String, Object> before = career(careerId);
		String token = token(userId, "STUDENT");
		for (String id : List.of("not-a-number", "1.5", "9223372036854775808")) {
			assertError(send("DELETE", CAREER_PATH + id, token), 400, "INVALID_INPUT");
		}
		assertThat(career(careerId)).isEqualTo(before);
	}

	@Test
	void concurrentDeletesReturnOneSuccessAndOneNotFound() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		String token = token(userId, "STUDENT");
		CompletableFuture<HttpResponse<String>> first;
		CompletableFuture<HttpResponse<String>> second;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from student_career where id = ? for update")) {
				statement.setLong(1, careerId);
				statement.executeQuery().close();
			}
			try {
				first = client.sendAsync(request("DELETE", CAREER_PATH + careerId, token), HttpResponse.BodyHandlers.ofString());
				second = client.sendAsync(request("DELETE", CAREER_PATH + careerId, token), HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%student_career%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally {
				connection.rollback();
			}
		}
		List<HttpResponse<String>> responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(204, 404);
		for (HttpResponse<String> response : responses) {
			if (response.statusCode() == 204) {
				assertThat(response.body()).isEmpty();
			} else {
				assertError(response, 404, "STUDENT_CAREER_NOT_FOUND");
			}
		}
		assertThat(jdbc.queryForObject("select count(*) from student_career where id = ?", Integer.class, careerId)).isZero();
	}

	@Test
	void keepsCareerAndReturnsSafeErrorWhenDatabaseDeleteFails() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		Map<String, Object> before = career(careerId);
		jdbc.execute("create table test_career_reference (career_id bigint references student_career(id))");
		try {
			jdbc.update("insert into test_career_reference (career_id) values (?)", careerId);
			HttpResponse<String> response = delete(careerId, token(userId, "STUDENT"));
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(career(careerId)).isEqualTo(before);
			assertThat(jdbc.queryForObject("select count(*) from test_career_reference", Integer.class)).isEqualTo(1);
		} finally {
			jdbc.execute("drop table test_career_reference");
		}
	}

	private long createUser(String role, boolean withProfile) {
		long id = jdbc.queryForObject("insert into user_account(email, role, name) values (?, ?, '테스트 이름') returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
		if (withProfile) {
			jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
					String.format(java.util.Locale.ROOT, "010%08d", id), id);
			jdbc.update("""
					insert into student_profile(id, birth_date, nationality, current_region, korean_level)
					values (?, '2000-03-14', 'VN', '서울 마포구', 'INTERMEDIATE')
					""", id);
		}
		return id;
	}

	private long createCareer(long userId) {
		return jdbc.queryForObject("""
				insert into student_career(user_id, career_name, start_month, end_month, description)
				values (?, '카페 아르바이트', '2023-03', '2023-09', '음료 제조') returning id
				""", Long.class, userId);
	}

	private Map<String, Object> career(long careerId) {
		return jdbc.queryForMap("select * from student_career where id = ?", careerId);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> delete(long careerId, String token) throws Exception {
		return send("DELETE", CAREER_PATH + careerId, token);
	}

	private HttpResponse<String> send(String method, String path, String token) throws Exception {
		return client.send(request(method, path, token), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String method, String path, String token) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(15)).method(method, HttpRequest.BodyPublishers.noBody());
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return request.build();
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
