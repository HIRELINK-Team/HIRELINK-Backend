package com.hirelink.domain.employer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
class EmployerProfileUpdateIntegrationTest {

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
		jdbc.execute("TRUNCATE TABLE employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void updatesOnlyProvidedFieldsAndReturnsTheSameInformationAsGet() throws Exception {
		long userId = createUser("EMPLOYER", true);
		long otherId = createUser("EMPLOYER", true);
		String token = token(userId, "EMPLOYER");
		Map<String, Object> memberBefore = member(userId);
		Map<String, Object> otherBefore = member(otherId);
		String longestName = "가".repeat(100);
		String longestPosition = "나".repeat(50);

		HttpResponse<String> nameOnly = patch(token, mapper.writeValueAsString(Map.of("name", longestName)));
		assertThat(nameOnly.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(nameOnly.body()).path("name").asString()).isEqualTo(longestName);
		assertThat(mapper.readTree(nameOnly.body()).path("position").asString()).isEqualTo("대표");
		HttpResponse<String> positionOnly = patch(token, mapper.writeValueAsString(Map.of("position", longestPosition)));
		assertThat(positionOnly.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(positionOnly.body()).path("name").asString()).isEqualTo(longestName);
		assertThat(mapper.readTree(positionOnly.body()).path("position").asString()).isEqualTo(longestPosition);

		HttpResponse<String> both = patch(token, mapper.writeValueAsString(Map.of("name", "수정 이름", "position", "점장")));
		assertThat(both.statusCode()).isEqualTo(200);
		assertThat(both.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(both.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("employerId", "name", "email", "phoneNumber", "position");
		assertThat(body.path("employerId").asLong()).isEqualTo(userId);
		assertThat(body.path("email").asString()).isEqualTo(memberBefore.get("email"));
		assertThat(body.path("phoneNumber").asString()).isEqualTo("010-0000-0001");
		assertThat(body.path("name").asString()).isEqualTo("수정 이름");
		assertThat(body.path("position").asString()).isEqualTo("점장");
		HttpRequest get = HttpRequest.newBuilder(endpoint()).header("Authorization", "Bearer " + token)
				.timeout(Duration.ofSeconds(15)).GET().build();
		HttpResponse<String> read = client.send(get, HttpResponse.BodyHandlers.ofString());
		assertThat(read.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(read.body())).isEqualTo(body);
		Map<String, Object> memberAfter = member(userId);
		assertThat(memberAfter.get("name")).isEqualTo("수정 이름");
		memberBefore.remove("name");
		memberBefore.remove("updated_at");
		memberAfter.remove("name");
		memberAfter.remove("updated_at");
		assertThat(memberAfter).isEqualTo(memberBefore);
		assertThat(member(otherId)).isEqualTo(otherBefore);
		assertThat(profile(otherId).get("position")).isEqualTo("대표");
	}

	@Test
	void explicitNullClearsPositionWhileOmissionPreservesIt() throws Exception {
		long userId = createUser("EMPLOYER", true);
		String token = token(userId, "EMPLOYER");
		for (int attempt = 0; attempt < 2; attempt++) {
			HttpResponse<String> response = patch(token, "{\"position\":null}");
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(mapper.readTree(response.body()).path("position").isNull()).isTrue();
			assertThat(member(userId).get("name")).isEqualTo("기존 이름");
			assertThat(profile(userId).get("position")).isNull();
		}
		assertThat(patch(token, "{\"name\":\"새 이름\"}").statusCode()).isEqualTo(200);
		assertThat(profile(userId).get("position")).isNull();
	}

	@Test
	void rejectsInvalidEmptyAndUnknownInputsWithoutChangingData() throws Exception {
		long userId = createUser("EMPLOYER", true);
		String token = token(userId, "EMPLOYER");
		Map<String, Object> memberBefore = member(userId);
		Map<String, Object> profileBefore = profile(userId);
		Object[][] invalidFields = {
				{"name", null}, {"name", ""}, {"name", " \t\n"}, {"name", "\u3000"}, {"name", "가".repeat(101)},
				{"name", 123}, {"name", true}, {"name", List.of("이름")},
				{"position", ""}, {"position", " \t\n"}, {"position", "나".repeat(51)},
				{"position", 123}, {"position", false}, {"position", Map.of("value", "대표")},
				{"userId", 999L}, {"email", "changed@example.com"}, {"phoneNumber", "010-0000-0000"},
				{"acquisitionChannel", "OTHER"}, {"preferredLanguage", "EN"}, {"positionProvided", true}
		};
		for (Object[] field : invalidFields) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("name", "수정 이름");
			body.put((String) field[0], field[1]);
			assertError(patch(token, mapper.writeValueAsString(body)), 400, "INVALID_INPUT");
		}
		for (String body : List.of("", "{", "{}", "[]", "null")) {
			assertError(patch(token, body), 400, "INVALID_INPUT");
		}
		assertThat(member(userId)).isEqualTo(memberBefore);
		assertThat(profile(userId)).isEqualTo(profileBefore);
	}

	@Test
	void requiresAuthenticationCurrentEmployerRoleAndOwnProfile() throws Exception {
		String body = "{\"name\":\"수정 이름\"}";
		assertError(patch(null, body), 401, "UNAUTHORIZED");
		assertError(patch("invalid-token", body), 401, "UNAUTHORIZED");
		assertError(patch(token(999L, "EMPLOYER"), body), 401, "UNAUTHORIZED");
		long userId = createUser("STUDENT", true);
		assertError(patch(token(userId, "STUDENT"), body), 403, "FORBIDDEN");
		assertError(patch(token(userId, "EMPLOYER"), body), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'EMPLOYER', withdrawn_at = now() where id = ?", userId);
		assertError(patch(token(userId, "EMPLOYER"), body), 403, "WITHDRAWN_ACCOUNT");
		long missingProfileId = createUser("EMPLOYER", false);
		assertError(patch(token(missingProfileId, "EMPLOYER"), body), 404, "EMPLOYER_PROFILE_NOT_FOUND");
		assertThat(member(userId).get("name")).isEqualTo("기존 이름");
		assertThat(member(missingProfileId).get("name")).isEqualTo("기존 이름");
		assertThat(jdbc.queryForObject("select id from employer_profile", Long.class)).isEqualTo(userId);
	}

	@Test
	void positionSaveFailureRollsBackBothNameAndPosition() throws Exception {
		long userId = createUser("EMPLOYER", true);
		Map<String, Object> memberBefore = member(userId);
		Map<String, Object> profileBefore = profile(userId);
		jdbc.execute("alter table employer_profile add constraint test_position_update_failure check (position <> '점장')");
		try {
			HttpResponse<String> response = patch(token(userId, "EMPLOYER"),
					mapper.writeValueAsString(Map.of("name", "수정 이름", "position", "점장")));
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(member(userId)).isEqualTo(memberBefore);
			assertThat(profile(userId)).isEqualTo(profileBefore);
		} finally {
			jdbc.execute("alter table employer_profile drop constraint test_position_update_failure");
		}
	}

	@Test
	void concurrentPartialUpdatesPreserveBothChanges() throws Exception {
		long userId = createUser("EMPLOYER", true);
		String token = token(userId, "EMPLOYER");
		CompletableFuture<HttpResponse<String>> nameRequest = client.sendAsync(
				patchRequest(token, "{\"name\":\"수정 이름\"}"), HttpResponse.BodyHandlers.ofString());
		CompletableFuture<HttpResponse<String>> positionRequest = client.sendAsync(
				patchRequest(token, "{\"position\":\"점장\"}"), HttpResponse.BodyHandlers.ofString());
		assertThat(nameRequest.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(positionRequest.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(member(userId).get("name")).isEqualTo("수정 이름");
		assertThat(profile(userId).get("position")).isEqualTo("점장");
	}

	private long createUser(String role, boolean withProfile) {
		Long userId = jdbc.queryForObject("""
				insert into user_account(email, role, name, acquisition_channel)
				values (?, ?, '기존 이름', 'REFERRAL') returning id
				""", Long.class, UUID.randomUUID() + "@example.com", role);
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
				String.format(java.util.Locale.ROOT, "010%08d", userId), userId);
		if (withProfile) {
			jdbc.update("insert into employer_profile(id, position) values (?, '대표')", userId);
		}
		return userId;
	}

	private Map<String, Object> member(long userId) {
		return jdbc.queryForMap("select * from user_account where id = ?", userId);
	}

	private Map<String, Object> profile(long userId) {
		return jdbc.queryForMap("select * from employer_profile where id = ?", userId);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private URI endpoint() {
		return URI.create("http://localhost:" + port + "/api/employers/me/profile");
	}

	private HttpResponse<String> patch(String token, String body) throws Exception {
		return client.send(patchRequest(token, body), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest patchRequest(String token, String body) {
		HttpRequest.Builder request = HttpRequest.newBuilder(endpoint()).timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers.ofString(body));
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
