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
class EmployerProfileCreateIntegrationTest {

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
				+ "student_job_preference, student_priority, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void createsProfileWithSharedUserIdAndAllSupportedChannels() throws Exception {
		for (String channel : List.of("SEARCH", "REFERRAL", "OTHER")) {
			long userId = createUser("EMPLOYER", true);
			Map<String, Object> request = validRequest();
			request.put("acquisitionChannel", channel);
			request.put("name", "가".repeat(100));
			HttpResponse<String> response = create(token(userId, "EMPLOYER"), mapper.writeValueAsString(request));

			assertThat(response.statusCode()).isEqualTo(201);
			assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
			JsonNode body = mapper.readTree(response.body());
			assertThat(body.size()).isEqualTo(6);
			assertThat(body.path("employerId").asLong()).isEqualTo(userId);
			assertThat(body.path("name").asString()).isEqualTo(request.get("name"));
			assertThat(body.path("email").asString())
					.isEqualTo(jdbc.queryForObject("select email from user_account where id = ?", String.class, userId));
			assertThat(body.path("phoneNumber").asString())
					.isEqualTo(String.format(java.util.Locale.ROOT, "010-0000-%04d", userId));
			assertThat(body.path("acquisitionChannel").asString()).isEqualTo(channel);
			assertThat(body.path("position").isNull()).isTrue();
			assertThat(jdbc.queryForMap("select * from employer_profile where id = ?", userId))
					.hasSize(2).containsEntry("id", userId).containsEntry("position", null);
			assertThat(jdbc.queryForMap("select name, acquisition_channel, phone_number from user_account where id = ?", userId))
					.containsEntry("name", request.get("name")).containsEntry("acquisition_channel", channel)
					.containsEntry("phone_number", String.format(java.util.Locale.ROOT, "010%08d", userId));
		}
		assertThat(jdbc.queryForObject("select count(*) from workplace", Integer.class)).isZero();
	}

	@Test
	void rejectsInvalidOrUnknownInputsWithoutSaving() throws Exception {
		long userId = createUser("EMPLOYER", true);
		String token = token(userId, "EMPLOYER");
		Object[][] invalidFields = {
				{"name", null}, {"name", "   "}, {"name", "가".repeat(101)}, {"name", 123},
				{"acquisitionChannel", null}, {"acquisitionChannel", ""}, {"acquisitionChannel", "referral"},
				{"acquisitionChannel", " SEARCH "}, {"acquisitionChannel", "INVALID"},
				{"userId", 999L}, {"phoneNumber", "010-0000-0000"}, {"position", null}, {"unknown", true}
		};
		for (Object[] field : invalidFields) {
			Map<String, Object> request = validRequest();
			request.put((String) field[0], field[1]);
			assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		for (String missing : List.of("name", "acquisitionChannel")) {
			Map<String, Object> request = validRequest();
			request.remove(missing);
			assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		for (String body : List.of("", "{", "{}", "[]")) {
			assertError(create(token, body), 400, "INVALID_INPUT");
		}
		assertUnchanged(userId);
	}

	@Test
	void requiresAuthenticationAndCurrentEmployerRole() throws Exception {
		String body = mapper.writeValueAsString(validRequest());
		assertError(create(null, body), 401, "UNAUTHORIZED");
		assertError(create("invalid-token", body), 401, "UNAUTHORIZED");
		assertError(create(token(999L, "EMPLOYER"), body), 401, "UNAUTHORIZED");
		long userId = createUser("STUDENT", true);
		assertError(create(token(userId, "STUDENT"), body), 403, "FORBIDDEN");
		assertError(create(token(userId, "EMPLOYER"), body), 403, "FORBIDDEN");
		assertUnchanged(userId);
		jdbc.update("update user_account set role = 'EMPLOYER', withdrawn_at = now() where id = ?", userId);
		assertError(create(token(userId, "EMPLOYER"), body), 403, "WITHDRAWN_ACCOUNT");
		assertUnchanged(userId);
	}

	@Test
	void requiresBothPhoneNumberAndVerificationTime() throws Exception {
		long userId = createUser("EMPLOYER", false);
		String token = token(userId, "EMPLOYER");
		String body = mapper.writeValueAsString(validRequest());
		assertError(create(token, body), 409, "PHONE_NOT_VERIFIED");
		jdbc.update("update user_account set phone_number = '01000000001' where id = ?", userId);
		assertError(create(token, body), 409, "PHONE_NOT_VERIFIED");
		jdbc.update("update user_account set phone_number = null, phone_verified_at = now() where id = ?", userId);
		assertError(create(token, body), 409, "PHONE_NOT_VERIFIED");
		assertUnchanged(userId);
	}

	@Test
	void rejectsDuplicateWithoutOverwritingExistingInformation() throws Exception {
		long userId = createUser("EMPLOYER", true);
		long otherUserId = createUser("EMPLOYER", true);
		String token = token(userId, "EMPLOYER");
		assertThat(create(token, mapper.writeValueAsString(validRequest())).statusCode()).isEqualTo(201);
		jdbc.update("update employer_profile set position = '대표' where id = ?", userId);
		Map<String, Object> request = Map.of("name", "변경 이름", "acquisitionChannel", "SEARCH");
		assertError(create(token, mapper.writeValueAsString(request)), 409, "DUPLICATE_EMPLOYER_PROFILE");
		assertThat(jdbc.queryForMap("select name, acquisition_channel from user_account where id = ?", userId))
				.containsEntry("name", "테스트 이름").containsEntry("acquisition_channel", "REFERRAL");
		assertThat(jdbc.queryForObject("select position from employer_profile where id = ?", String.class, userId))
				.isEqualTo("대표");
		assertThat(jdbc.queryForObject("select count(*) from employer_profile", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForMap("select name, acquisition_channel from user_account where id = ?", otherUserId))
				.containsEntry("name", null).containsEntry("acquisition_channel", null);
	}

	@Test
	void simultaneousRequestsCreateOnlyOneProfile() throws Exception {
		long userId = createUser("EMPLOYER", true);
		String token = token(userId, "EMPLOYER");
		HttpRequest firstRequest = httpRequest(token, mapper.writeValueAsString(validRequest()));
		HttpRequest secondRequest = httpRequest(token,
				mapper.writeValueAsString(Map.of("name", "다른 이름", "acquisitionChannel", "OTHER")));
		CompletableFuture<HttpResponse<String>> one = client.sendAsync(firstRequest, HttpResponse.BodyHandlers.ofString());
		CompletableFuture<HttpResponse<String>> two = client.sendAsync(secondRequest, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> first = one.get(15, TimeUnit.SECONDS);
		HttpResponse<String> second = two.get(15, TimeUnit.SECONDS);
		assertThat(new int[] {first.statusCode(), second.statusCode()}).containsExactlyInAnyOrder(201, 409);
		assertError(first.statusCode() == 409 ? first : second, 409, "DUPLICATE_EMPLOYER_PROFILE");
		JsonNode saved = mapper.readTree(first.statusCode() == 201 ? first.body() : second.body());
		assertThat(jdbc.queryForMap("select name, acquisition_channel from user_account where id = ?", userId))
				.containsEntry("name", saved.path("name").asString())
				.containsEntry("acquisition_channel", saved.path("acquisitionChannel").asString());
		assertThat(jdbc.queryForObject("select count(*) from employer_profile", Integer.class)).isEqualTo(1);
	}

	@Test
	void memberUpdateFailureRollsBackProfileAndBothMemberFields() throws Exception {
		long userId = createUser("EMPLOYER", true);
		jdbc.update("update user_account set name = '기존 이름', acquisition_channel = 'SEARCH' where id = ?", userId);
		jdbc.execute("alter table user_account add constraint test_channel_update_failure check (acquisition_channel <> 'REFERRAL')");
		try {
			HttpResponse<String> response = create(token(userId, "EMPLOYER"), mapper.writeValueAsString(validRequest()));
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(jdbc.queryForObject("select count(*) from employer_profile", Integer.class)).isZero();
			assertThat(jdbc.queryForMap("select name, acquisition_channel from user_account where id = ?", userId))
					.containsEntry("name", "기존 이름").containsEntry("acquisition_channel", "SEARCH");
		} finally {
			jdbc.execute("alter table user_account drop constraint test_channel_update_failure");
		}
	}

	private Map<String, Object> validRequest() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", "테스트 이름");
		body.put("acquisitionChannel", "REFERRAL");
		return body;
	}

	private long createUser(String role, boolean verified) {
		Long userId = jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
		if (verified) {
			jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
					String.format(java.util.Locale.ROOT, "010%08d", userId), userId);
		}
		return userId;
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> create(String token, String body) throws Exception {
		return client.send(httpRequest(token, body), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest httpRequest(String token, String body) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/employers/me/profile"))
				.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			builder.header("Authorization", "Bearer " + token);
		}
		return builder.build();
	}

	private void assertUnchanged(long userId) {
		assertThat(jdbc.queryForObject("select count(*) from employer_profile", Integer.class)).isZero();
		assertThat(jdbc.queryForMap("select name, acquisition_channel from user_account where id = ?", userId))
				.containsEntry("name", null).containsEntry("acquisition_channel", null);
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
