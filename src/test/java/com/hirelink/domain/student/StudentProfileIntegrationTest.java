package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentProfileIntegrationTest {

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
		jdbc.execute("TRUNCATE TABLE student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@ParameterizedTest
	@ValueSource(strings = {"BEGINNER", "INTERMEDIATE", "ADVANCED"})
	void createsProfileWithSharedIdAndKeepsOnboardingFieldsNull(String koreanLevel) throws Exception {
		long userId = createUser("STUDENT", true);
		Map<String, Object> request = validRequest();
		request.put("koreanLevel", koreanLevel);
		HttpResponse<String> response = create(token(userId, "STUDENT"), mapper.writeValueAsString(request));

		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(7);
		assertThat(body.path("studentId").asLong()).isEqualTo(userId);
		assertThat(body.path("name").asString()).isEqualTo(request.get("name"));
		assertThat(body.path("phoneNumber").asString()).isEqualTo("010-0000-0001");
		assertThat(body.path("birthDate").asString()).isEqualTo(request.get("birthDate"));
		assertThat(body.path("nationality").asString()).isEqualTo("VN");
		assertThat(body.path("currentRegion").asString()).isEqualTo(request.get("currentRegion"));
		assertThat(body.path("koreanLevel").asString()).isEqualTo(koreanLevel);
		assertThat(jdbc.queryForObject("select name from user_account where id = ?", String.class, userId))
				.isEqualTo(request.get("name"));

		Map<String, Object> stored = jdbc.queryForMap("select * from student_profile where id = ?", userId);
		assertThat(stored).hasSize(17);
		assertThat(stored.get("birth_date").toString()).isEqualTo(request.get("birthDate"));
		assertThat(stored).containsEntry("nationality", "VN").containsEntry("korean_level", koreanLevel)
				.containsEntry("current_region", request.get("currentRegion"));
		assertThat(stored).containsEntry("visa_code", null).containsEntry("university_name", null)
				.containsEntry("enrollment_start_date", null).containsEntry("study_program_id", null)
				.containsEntry("grade_level", null).containsEntry("academic_state", null).containsEntry("topik_level", null)
				.containsEntry("stay_start_date", null).containsEntry("stay_expiration_date", null)
				.containsEntry("profile_image_key", null).containsEntry("certification_prompt_dismissed_at", null)
				.containsEntry("hirelink_badge_issued_at", null);
		assertThat(jdbc.queryForObject("select count(*) from visa_type", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("select count(*) from study_program", Integer.class)).isZero();
	}

	@Test
	void acceptsEighteenthBirthdayAndRejectsDayBeforeItInKorea() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		LocalDate threshold = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(18);
		Map<String, Object> request = validRequest();
		request.put("birthDate", threshold.plusDays(1).toString());
		assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		assertUnchanged(userId);
		request.put("birthDate", threshold.toString());
		assertThat(create(token, mapper.writeValueAsString(request)).statusCode()).isEqualTo(201);
	}

	@ParameterizedTest
	@MethodSource("invalidFields")
	void rejectsInvalidFieldsWithoutSaving(String field, Object value) throws Exception {
		long userId = createUser("STUDENT", true);
		Map<String, Object> request = validRequest();
		request.put(field, value);
		assertError(create(token(userId, "STUDENT"), mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		assertUnchanged(userId);
	}

	static Stream<Arguments> invalidFields() {
		return Stream.of(
				Arguments.of("name", null),
				Arguments.of("name", "   "),
				Arguments.of("name", "가".repeat(101)),
				Arguments.of("birthDate", null),
				Arguments.of("birthDate", "2000-02-30"),
				Arguments.of("birthDate", LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString()),
				Arguments.of("nationality", null),
				Arguments.of("nationality", "vn"),
				Arguments.of("nationality", "ZZ"),
				Arguments.of("currentRegion", " "),
				Arguments.of("currentRegion", "가".repeat(151)),
				Arguments.of("koreanLevel", null),
				Arguments.of("koreanLevel", "FLUENT"),
				Arguments.of("koreanLevel", 0));
	}

	@Test
	void rejectsMissingBodyAndMalformedJson() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		assertError(create(token, ""), 400, "INVALID_INPUT");
		assertError(create(token, "{"), 400, "INVALID_INPUT");
		assertError(create(token, "{}"), 400, "INVALID_INPUT");
		assertUnchanged(userId);
	}

	@Test
	void requiresAuthenticationAndCurrentStudentRole() throws Exception {
		String body = mapper.writeValueAsString(validRequest());
		assertError(create(null, body), 401, "UNAUTHORIZED");
		assertError(create("invalid-token", body), 401, "UNAUTHORIZED");
		assertError(create(token(999L, "STUDENT"), body), 401, "UNAUTHORIZED");
		long userId = createUser("EMPLOYER", true);
		assertError(create(token(userId, "EMPLOYER"), body), 403, "FORBIDDEN");
		assertError(create(token(userId, "STUDENT"), body), 403, "FORBIDDEN");
		assertUnchanged(userId);
		jdbc.update("update user_account set role = 'STUDENT', withdrawn_at = now() where id = ?", userId);
		assertError(create(token(userId, "STUDENT"), body), 403, "WITHDRAWN_ACCOUNT");
		assertUnchanged(userId);
	}

	@ParameterizedTest
	@ValueSource(strings = {"both", "number", "verifiedAt"})
	void requiresBothVerifiedPhoneFields(String missing) throws Exception {
		long userId = createUser("STUDENT", false);
		if (missing.equals("number")) {
			jdbc.update("update user_account set phone_verified_at = now() where id = ?", userId);
		} else if (missing.equals("verifiedAt")) {
			jdbc.update("update user_account set phone_number = '01000000001' where id = ?", userId);
		}
		assertError(create(token(userId, "STUDENT"), mapper.writeValueAsString(validRequest())), 409, "PHONE_NOT_VERIFIED");
		assertUnchanged(userId);
	}

	@Test
	void usesTokenUserAndRejectsDuplicateWithoutOverwritingName() throws Exception {
		long userId = createUser("STUDENT", true);
		long otherUserId = createUser("STUDENT", false);
		String token = token(userId, "STUDENT");
		Map<String, Object> request = validRequest();
		request.put("userId", otherUserId);
		assertThat(create(token, mapper.writeValueAsString(request)).statusCode()).isEqualTo(201);
		assertThat(jdbc.queryForObject("select id from student_profile", Long.class)).isEqualTo(userId);
		assertThat(jdbc.queryForObject("select name from user_account where id = ?", String.class, otherUserId)).isNull();
		request.put("name", "바뀐 이름");
		assertError(create(token, mapper.writeValueAsString(request)), 409, "DUPLICATE_STUDENT_PROFILE");
		assertThat(jdbc.queryForObject("select name from user_account where id = ?", String.class, userId))
				.isEqualTo("테스트 이름");
		assertThat(jdbc.queryForObject("select count(*) from student_profile", Integer.class)).isEqualTo(1);
	}

	@Test
	void simultaneousRequestsCreateOnlyOneProfile() throws Exception {
		long userId = createUser("STUDENT", true);
		HttpRequest request = httpRequest(token(userId, "STUDENT"), mapper.writeValueAsString(validRequest()));
		CompletableFuture<HttpResponse<String>> one = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		CompletableFuture<HttpResponse<String>> two = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> first = one.get(15, TimeUnit.SECONDS);
		HttpResponse<String> second = two.get(15, TimeUnit.SECONDS);
		assertThat(new int[] {first.statusCode(), second.statusCode()}).containsExactlyInAnyOrder(201, 409);
		assertError(first.statusCode() == 409 ? first : second, 409, "DUPLICATE_STUDENT_PROFILE");
		assertThat(jdbc.queryForObject("select count(*) from student_profile", Integer.class)).isEqualTo(1);
	}

	@Test
	void nameUpdateFailureRollsBackProfileInsert() throws Exception {
		long userId = createUser("STUDENT", true);
		jdbc.update("update user_account set name = '기존 이름' where id = ?", userId);
		// 일회용 테스트 DB에서 이름 변경 실패를 발생시켜 프로필 생성까지 취소되는지 확인한다.
		jdbc.execute("alter table user_account add constraint test_name_update_failure check (name <> '테스트 이름')");
		try {
			HttpResponse<String> response = create(token(userId, "STUDENT"), mapper.writeValueAsString(validRequest()));
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(jdbc.queryForObject("select count(*) from student_profile", Integer.class)).isZero();
			assertThat(jdbc.queryForObject("select name from user_account where id = ?", String.class, userId))
					.isEqualTo("기존 이름");
		} finally {
			jdbc.execute("alter table user_account drop constraint test_name_update_failure");
		}
	}

	private Map<String, Object> validRequest() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", "테스트 이름");
		body.put("birthDate", "2000-03-14");
		body.put("nationality", "VN");
		body.put("currentRegion", "서울 마포구");
		body.put("koreanLevel", "INTERMEDIATE");
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
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/students/me/profile"))
				.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			builder.header("Authorization", "Bearer " + token);
		}
		return builder.build();
	}

	private void assertUnchanged(long userId) {
		assertThat(jdbc.queryForObject("select count(*) from student_profile", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select name from user_account where id = ?", String.class, userId)).isNull();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
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
