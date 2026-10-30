package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
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
class StudentCareerUpdateIntegrationTest {

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
		jdbc.execute("TRUNCATE TABLE job_posting, workplace, student_career, student_profile, user_term_agreement, "
				+ "phone_verification, phone_verification_send_attempt, refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void updatesOnlyProvidedFieldsAndReflectsTheResultInProfile() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		long otherCareerId = createCareer(createUser("STUDENT", true));
		Map<String, Object> otherBefore = career(otherCareerId);
		Map<String, Object> userBefore = jdbc.queryForMap("select * from user_account where id = ?", userId);
		Map<String, Object> profileBefore = jdbc.queryForMap("select * from student_profile where id = ?", userId);
		String token = token(userId, "STUDENT");

		HttpResponse<String> response = patch(careerId, token, "{\"endMonth\":\"2023-10\"}");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode updated = mapper.readTree(response.body());
		assertThat(updated).isEqualTo(mapper.readTree("""
				{"careerId":%d,"careerName":"카페 아르바이트","startMonth":"2023-03",
				"endMonth":"2023-10","description":"음료 제조"}
				""".formatted(careerId)));
		assertThat(career(careerId)).containsEntry("user_id", userId).containsEntry("end_month", "2023-10");
		assertThat(career(otherCareerId)).isEqualTo(otherBefore);
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(userBefore);
		assertThat(jdbc.queryForMap("select * from student_profile where id = ?", userId)).isEqualTo(profileBefore);

		HttpResponse<String> read = send("GET", "/api/students/me/profile", token, null);
		assertThat(read.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(read.body()).path("careers").size()).isEqualTo(1);
		assertThat(mapper.readTree(read.body()).path("careers").get(0)).isEqualTo(updated);
	}

	@Test
	void distinguishesOmittedDescriptionFromExplicitNullAndAcceptsRepeatedValues() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		String token = token(userId, "STUDENT");
		String json = "{\"careerName\":\"매장 아르바이트\"}";
		HttpResponse<String> first = patch(careerId, token, json);
		HttpResponse<String> repeated = patch(careerId, token, json);
		assertThat(first.statusCode()).isEqualTo(200);
		assertThat(repeated.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(repeated.body())).isEqualTo(mapper.readTree(first.body()));
		assertThat(career(careerId)).containsEntry("description", "음료 제조");

		HttpResponse<String> cleared = patch(careerId, token, "{\"description\":null}");
		assertThat(cleared.statusCode()).isEqualTo(200);
		JsonNode body = mapper.readTree(cleared.body());
		assertThat(body.has("description")).isTrue();
		assertThat(body.path("description").isNull()).isTrue();
		assertThat(career(careerId)).containsEntry("description", null).containsEntry("career_name", "매장 아르바이트")
				.containsEntry("start_month", "2023-03").containsEntry("end_month", "2023-09");
		assertThat(patch(careerId, token, "{\"description\":\"\"}").statusCode()).isEqualTo(200);
		assertThat(career(careerId)).containsEntry("description", "");
	}

	@Test
	void acceptsMaximumNameLengthAndSameStartAndEndAtCurrentMonth() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		String month = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		String json = mapper.writeValueAsString(Map.of("careerName", "가".repeat(150), "startMonth", month, "endMonth", month));
		HttpResponse<String> response = patch(careerId, token(userId, "STUDENT"), json);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(career(careerId)).containsEntry("career_name", "가".repeat(150))
				.containsEntry("start_month", month).containsEntry("end_month", month);
	}

	@Test
	void rejectsEmptyUnknownNullBlankAndWrongTypedValuesWithoutChangingData() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		String token = token(userId, "STUDENT");
		Map<String, Object> before = career(careerId);
		for (String json : List.of("{}", "[]", "null", "{")) {
			assertError(patch(careerId, token, json), 400, "INVALID_INPUT");
		}
		for (String name : List.of("", "   ", "가".repeat(151))) {
			assertError(patch(careerId, token, mapper.writeValueAsString(Map.of("careerName", name))), 400, "INVALID_INPUT");
		}
		for (String field : List.of("careerName", "startMonth", "endMonth")) {
			Map<String, Object> request = new LinkedHashMap<>();
			request.put(field, null);
			assertError(patch(careerId, token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		for (String field : List.of("careerName", "startMonth", "endMonth", "description")) {
			for (Object value : List.of(123, true, List.of("text"), Map.of("value", "text"))) {
				assertError(patch(careerId, token, mapper.writeValueAsString(Map.of(field, value))), 400, "INVALID_INPUT");
			}
		}
		for (String field : List.of("userId", "careerId", "unknown", "descriptionProvided")) {
			assertError(patch(careerId, token, mapper.writeValueAsString(Map.of(field, 999))), 400, "INVALID_INPUT");
		}
		assertError(send("PATCH", CAREER_PATH + "not-a-number", token, "{\"description\":null}"), 400, "INVALID_INPUT");
		assertThat(career(careerId)).isEqualTo(before);
	}

	@Test
	void validatesMonthFormatAndFinalPeriodIncludingUnchangedValues() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		String token = token(userId, "STUDENT");
		Map<String, Object> before = career(careerId);
		for (String field : List.of("startMonth", "endMonth")) {
			for (String month : List.of("2023-00", "2023-13", "2023-3", "2023/03", "2023-03-01", "99999-03", "")) {
				assertError(patch(careerId, token, mapper.writeValueAsString(Map.of(field, month))), 400, "INVALID_INPUT");
			}
		}
		String future = YearMonth.now(ZoneId.of("Asia/Seoul")).plusMonths(1).toString();
		for (Map<String, String> request : List.of(Map.of("startMonth", "2023-10"), Map.of("endMonth", "2023-02"),
				Map.of("endMonth", future), Map.of("startMonth", future, "endMonth", future))) {
			assertError(patch(careerId, token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		assertThat(career(careerId)).isEqualTo(before);
	}

	@Test
	void requiresAuthenticationCurrentStudentRoleAndCareerOwnership() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		Map<String, Object> before = career(careerId);
		String json = "{\"description\":null}";
		assertError(patch(careerId, null, json), 401, "UNAUTHORIZED");
		assertError(patch(careerId, "invalid-token", json), 401, "UNAUTHORIZED");
		assertError(patch(careerId, token(99999, "STUDENT"), json), 401, "UNAUTHORIZED");
		long employer = createUser("EMPLOYER", false);
		assertError(patch(careerId, token(employer, "EMPLOYER"), json), 403, "FORBIDDEN");
		assertError(patch(careerId, token(employer, "STUDENT"), json), 403, "FORBIDDEN");
		long otherUserId = createUser("STUDENT", true);
		assertError(send("PATCH", CAREER_PATH + careerId + "?userId=" + userId,
				token(otherUserId, "STUDENT"), json), 403, "FORBIDDEN");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", userId);
		assertError(patch(careerId, token(userId, "STUDENT"), json), 403, "WITHDRAWN_ACCOUNT");
		assertThat(career(careerId)).isEqualTo(before);
	}

	@Test
	void returnsNotFoundForMissingCareer() throws Exception {
		long userId = createUser("STUDENT", true);
		createCareer(userId);
		assertError(patch(99999, token(userId, "STUDENT"), "{\"description\":null}"), 404, "STUDENT_CAREER_NOT_FOUND");
		assertError(patch(-1, token(userId, "STUDENT"), "{\"description\":null}"), 404, "STUDENT_CAREER_NOT_FOUND");
		assertThat(jdbc.queryForObject("select count(*) from student_career", Integer.class)).isEqualTo(1);
	}

	@Test
	void preservesDifferentFieldsWhenTwoRequestsUpdateSameCareer() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		String token = token(userId, "STUDENT");
		CompletableFuture<HttpResponse<String>> nameResponse;
		CompletableFuture<HttpResponse<String>> descriptionResponse;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from student_career where id = ? for update")) {
				statement.setLong(1, careerId);
				statement.executeQuery().close();
			}
			try {
				nameResponse = client.sendAsync(request("PATCH", CAREER_PATH + careerId, token,
						"{\"careerName\":\"매장 아르바이트\"}"), HttpResponse.BodyHandlers.ofString());
				descriptionResponse = client.sendAsync(request("PATCH", CAREER_PATH + careerId, token,
						"{\"description\":\"매장 정리\"}"), HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%student_career%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally {
				connection.rollback();
			}
		}
		assertThat(nameResponse.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(descriptionResponse.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(career(careerId)).containsEntry("career_name", "매장 아르바이트").containsEntry("description", "매장 정리")
				.containsEntry("start_month", "2023-03").containsEntry("end_month", "2023-09");
	}

	@Test
	void rollsBackAllChangesAndReturnsSafeErrorWhenDatabaseWriteFails() throws Exception {
		long userId = createUser("STUDENT", true);
		long careerId = createCareer(userId);
		Map<String, Object> before = career(careerId);
		jdbc.execute("alter table student_career add constraint test_reject_update check (career_name <> '수정된 경력')");
		try {
			HttpResponse<String> response = patch(careerId, token(userId, "STUDENT"),
					"{\"careerName\":\"수정된 경력\",\"endMonth\":\"2023-10\",\"description\":null}");
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(career(careerId)).isEqualTo(before);
		} finally {
			jdbc.execute("alter table student_career drop constraint test_reject_update");
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

	private HttpResponse<String> patch(long careerId, String token, String json) throws Exception {
		return send("PATCH", CAREER_PATH + careerId, token, json);
	}

	private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
		return client.send(request(method, path, token, json), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String method, String path, String token, String json) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(15));
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
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
