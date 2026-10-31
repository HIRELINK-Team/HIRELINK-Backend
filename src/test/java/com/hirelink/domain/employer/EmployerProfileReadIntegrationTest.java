package com.hirelink.domain.employer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmployerProfileReadIntegrationTest {

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
	private ObjectMapper mapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void returnsOnlyOwnProfileWithNullablePositionWithoutChangingData() throws Exception {
		long userId = createUser("EMPLOYER");
		long otherId = createUser("EMPLOYER");
		createProfile(userId);
		createProfile(otherId);
		jdbc.update("update user_account set name = '다른 회원' where id = ?", otherId);
		Map<String, Object> memberBefore = jdbc.queryForMap("select * from user_account where id = ?", userId);

		for (String position : new String[] {null, "대표"}) {
			jdbc.update("update employer_profile set position = ? where id = ?", position, userId);
			Map<String, Object> profileBefore = jdbc.queryForMap("select * from employer_profile where id = ?", userId);
			HttpResponse<String> response = get(token(userId, "EMPLOYER"), "?userId=" + otherId);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
			JsonNode body = mapper.readTree(response.body());
			assertThat(body.propertyNames()).containsExactlyInAnyOrder("employerId", "name", "email", "phoneNumber", "position");
			assertThat(body.path("employerId").asLong()).isEqualTo(userId);
			assertThat(body.path("name").asString()).isEqualTo("테스트 이름");
			assertThat(body.path("email").asString()).isEqualTo(memberBefore.get("email"));
			assertThat(body.path("phoneNumber").asString()).isEqualTo("010-0000-0001");
			if (position == null) {
				assertThat(body.path("position").isNull()).isTrue();
			} else {
				assertThat(body.path("position").asString()).isEqualTo(position);
			}
			assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(memberBefore);
			assertThat(jdbc.queryForMap("select * from employer_profile where id = ?", userId)).isEqualTo(profileBefore);
		}
	}

	@Test
	void requiresAuthenticationAndCurrentEmployerRole() throws Exception {
		assertError(get(null, ""), 401, "UNAUTHORIZED");
		assertError(get("invalid-token", ""), 401, "UNAUTHORIZED");
		assertError(get(token(999L, "EMPLOYER"), ""), 401, "UNAUTHORIZED");
		long userId = createUser("STUDENT");
		createProfile(userId);
		assertError(get(token(userId, "STUDENT"), ""), 403, "FORBIDDEN");
		assertError(get(token(userId, "EMPLOYER"), ""), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'EMPLOYER', withdrawn_at = now() where id = ?", userId);
		assertError(get(token(userId, "EMPLOYER"), ""), 403, "WITHDRAWN_ACCOUNT");
	}

	@Test
	void returnsNotFoundWithoutCreatingOrReturningAnotherProfile() throws Exception {
		long userId = createUser("EMPLOYER");
		long otherId = createUser("EMPLOYER");
		createProfile(otherId);
		Map<String, Object> memberBefore = jdbc.queryForMap("select * from user_account where id = ?", userId);
		assertError(get(token(userId, "EMPLOYER"), ""), 404, "EMPLOYER_PROFILE_NOT_FOUND");
		assertError(get(token(userId, "EMPLOYER"), "?userId=" + otherId), 404, "EMPLOYER_PROFILE_NOT_FOUND");
		assertThat(jdbc.queryForObject("select id from employer_profile", Long.class)).isEqualTo(otherId);
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(memberBefore);
	}

	@Test
	void databaseReadFailureReturnsGenericServerError() throws Exception {
		long userId = createUser("EMPLOYER");
		createProfile(userId);
		Map<String, Object> profileBefore = jdbc.queryForMap("select * from employer_profile where id = ?", userId);
		jdbc.execute("alter table employer_profile rename column position to test_unavailable_position");
		try {
			HttpResponse<String> response = get(token(userId, "EMPLOYER"), "");
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
		} finally {
			jdbc.execute("alter table employer_profile rename column test_unavailable_position to position");
		}
		assertThat(jdbc.queryForMap("select * from employer_profile where id = ?", userId)).isEqualTo(profileBefore);
	}

	private long createUser(String role) {
		Long userId = jdbc.queryForObject("""
				insert into user_account(email, role, name, acquisition_channel)
				values (?, ?, '테스트 이름', 'REFERRAL') returning id
				""", Long.class, UUID.randomUUID() + "@example.com", role);
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
				String.format(java.util.Locale.ROOT, "010%08d", userId), userId);
		return userId;
	}

	private void createProfile(long userId) {
		jdbc.update("insert into employer_profile(id) values (?)", userId);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> get(String token, String query) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/employers/me/profile" + query))
				.timeout(Duration.ofSeconds(15)).GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
