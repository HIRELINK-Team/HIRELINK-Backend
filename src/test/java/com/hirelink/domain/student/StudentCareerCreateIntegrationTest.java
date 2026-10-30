package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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
class StudentCareerCreateIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final String CAREER_PATH = "/api/students/me/careers";
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
		jdbc.execute("TRUNCATE TABLE student_career, student_profile, user_term_agreement, phone_verification, "
				+ "phone_verification_send_attempt, refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void createsOwnCareerAndReturnsItInProfileWithoutOtherMembersCareers() throws Exception {
		long userId = createUser("STUDENT", true);
		long otherId = createUser("STUDENT", true);
		Map<String, Object> userBefore = jdbc.queryForMap("select * from user_account where id = ?", userId);
		Map<String, Object> profileBefore = jdbc.queryForMap("select * from student_profile where id = ?", userId);
		String token = token(userId, "STUDENT");
		HttpResponse<String> response = send("POST", CAREER_PATH + "?userId=" + otherId, token,
				mapper.writeValueAsString(validRequest()));
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode created = mapper.readTree(response.body());
		assertThat(created.size()).isEqualTo(5);
		assertThat(created.path("careerId").asLong()).isPositive();
		for (var entry : validRequest().entrySet()) {
			assertThat(created.path(entry.getKey()).asString()).isEqualTo(entry.getValue());
		}
		Map<String, Object> saved = jdbc.queryForMap("select * from student_career where id = ?", created.path("careerId").asLong());
		assertThat(saved).containsEntry("user_id", userId).containsEntry("career_name", "카페 아르바이트")
				.containsEntry("start_month", "2023-03").containsEntry("end_month", "2023-09")
				.containsEntry("description", "음료 제조, 고객 응대");
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(userBefore);
		assertThat(jdbc.queryForMap("select * from student_profile where id = ?", userId)).isEqualTo(profileBefore);

		Map<String, Object> recent = validRequest();
		recent.put("startMonth", "2024-01");
		recent.put("endMonth", "2024-02");
		HttpResponse<String> second = create(token, mapper.writeValueAsString(recent));
		assertThat(second.statusCode()).isEqualTo(201);
		assertThat(create(token(otherId, "STUDENT"), mapper.writeValueAsString(validRequest())).statusCode()).isEqualTo(201);
		HttpResponse<String> read = send("GET", "/api/students/me/profile", token, null);
		assertThat(read.statusCode()).isEqualTo(200);
		JsonNode careers = mapper.readTree(read.body()).path("careers");
		assertThat(careers.size()).isEqualTo(2);
		assertThat(careers.get(0)).isEqualTo(mapper.readTree(second.body()));
		assertThat(careers.get(1)).isEqualTo(created);
	}

	@Test
	void permitsMissingOrNullDescriptionAndMultipleCareers() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		Map<String, Object> request = validRequest();
		request.remove("description");
		HttpResponse<String> missing = create(token, mapper.writeValueAsString(request));
		request.put("description", null);
		HttpResponse<String> explicitNull = create(token, mapper.writeValueAsString(request));
		assertThat(missing.statusCode()).isEqualTo(201);
		assertThat(explicitNull.statusCode()).isEqualTo(201);
		for (HttpResponse<String> response : List.of(missing, explicitNull)) {
			JsonNode body = mapper.readTree(response.body());
			assertThat(body.has("description")).isTrue();
			assertThat(body.path("description").isNull()).isTrue();
		}
		assertThat(mapper.readTree(missing.body()).path("careerId"))
				.isNotEqualTo(mapper.readTree(explicitNull.body()).path("careerId"));
		assertThat(jdbc.queryForObject("select count(*) from student_career where description is null", Integer.class)).isEqualTo(2);
	}

	@Test
	void acceptsSameStartAndEndMonthAtCurrentMonthAndMaximumNameLength() throws Exception {
		String currentMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		Map<String, Object> request = validRequest();
		request.put("careerName", "가".repeat(150));
		request.put("startMonth", currentMonth);
		request.put("endMonth", currentMonth);
		HttpResponse<String> response = create(token(createUser("STUDENT", true), "STUDENT"), mapper.writeValueAsString(request));
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(mapper.readTree(response.body()).path("startMonth").asString()).isEqualTo(currentMonth);
		assertThat(mapper.readTree(response.body()).path("endMonth").asString()).isEqualTo(currentMonth);
		assertThat(jdbc.queryForObject("select length(career_name) from student_career", Integer.class)).isEqualTo(150);
	}

	@Test
	void rejectsMissingNullBlankOrOversizedRequiredValues() throws Exception {
		String token = token(createUser("STUDENT", true), "STUDENT");
		for (String field : List.of("careerName", "startMonth", "endMonth")) {
			Map<String, Object> request = validRequest();
			request.remove(field);
			assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
			request.put(field, null);
			assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		for (String name : List.of("", "   ", "가".repeat(151))) {
			Map<String, Object> request = validRequest();
			request.put("careerName", name);
			assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		assertNoCareers();
	}

	@Test
	void rejectsMalformedMonthValuesForEitherEndpointOfPeriod() throws Exception {
		String token = token(createUser("STUDENT", true), "STUDENT");
		for (String month : List.of("2023-00", "2023-13", "2023-3", "2023/03", "2023-03-01", "99999-03", "")) {
			for (String field : List.of("startMonth", "endMonth")) {
				Map<String, Object> request = validRequest();
				request.put(field, month);
				assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
			}
		}
		assertNoCareers();
	}

	@Test
	void rejectsReversedAndFuturePeriodsWithoutSaving() throws Exception {
		String token = token(createUser("STUDENT", true), "STUDENT");
		YearMonth now = YearMonth.now(ZoneId.of("Asia/Seoul"));
		for (List<String> period : List.of(List.of("2023-09", "2023-03"),
				List.of(now.toString(), now.plusMonths(1).toString()),
				List.of(now.plusMonths(1).toString(), now.plusMonths(1).toString()))) {
			Map<String, Object> request = validRequest();
			request.put("startMonth", period.get(0));
			request.put("endMonth", period.get(1));
			assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		assertNoCareers();
	}

	@Test
	void rejectsWrongJsonTypesAndUnrecognizedFields() throws Exception {
		String token = token(createUser("STUDENT", true), "STUDENT");
		for (String json : List.of("{}", "[]", "null", "{")) {
			assertError(create(token, json), 400, "INVALID_INPUT");
		}
		for (String field : List.of("careerName", "startMonth", "endMonth", "description")) {
			Map<String, Object> request = validRequest();
			request.put(field, 123);
			assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		for (String field : List.of("userId", "careerId", "unknown")) {
			Map<String, Object> request = validRequest();
			request.put(field, 999);
			assertError(create(token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		assertNoCareers();
	}

	@Test
	void requiresAuthenticationAndCurrentActiveStudentRole() throws Exception {
		String json = mapper.writeValueAsString(validRequest());
		assertError(create(null, json), 401, "UNAUTHORIZED");
		assertError(create("invalid-token", json), 401, "UNAUTHORIZED");
		assertError(create(token(999, "STUDENT"), json), 401, "UNAUTHORIZED");
		long employer = createUser("EMPLOYER", true);
		assertError(create(token(employer, "EMPLOYER"), json), 403, "FORBIDDEN");
		assertError(create(token(employer, "STUDENT"), json), 403, "FORBIDDEN");
		long withdrawn = createUser("STUDENT", true);
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", withdrawn);
		assertError(create(token(withdrawn, "STUDENT"), json), 403, "WITHDRAWN_ACCOUNT");
		assertNoCareers();
	}

	@Test
	void requiresOwnProfileEvenWhenAnotherStudentHasOne() throws Exception {
		long userId = createUser("STUDENT", false);
		createUser("STUDENT", true);
		assertError(create(token(userId, "STUDENT"), mapper.writeValueAsString(validRequest())), 404, "STUDENT_PROFILE_NOT_FOUND");
		assertNoCareers();
	}

	@Test
	void returnsSafeServerErrorAndDoesNotSaveWhenDatabaseWriteFails() throws Exception {
		long userId = createUser("STUDENT", true);
		Map<String, Object> profileBefore = jdbc.queryForMap("select * from student_profile where id = ?", userId);
		jdbc.execute("alter table student_career add constraint test_reject_career check (career_name <> '카페 아르바이트')");
		try {
			HttpResponse<String> response = create(token(userId, "STUDENT"), mapper.writeValueAsString(validRequest()));
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertNoCareers();
			assertThat(jdbc.queryForMap("select * from student_profile where id = ?", userId)).isEqualTo(profileBefore);
		} finally {
			jdbc.execute("alter table student_career drop constraint test_reject_career");
		}
	}

	private Map<String, Object> validRequest() {
		return new LinkedHashMap<>(Map.of("careerName", "카페 아르바이트", "startMonth", "2023-03",
				"endMonth", "2023-09", "description", "음료 제조, 고객 응대"));
	}

	private long createUser(String role, boolean withProfile) {
		long id = jdbc.queryForObject("insert into user_account(email, role, name) values (?, ?, '테스트 이름') returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
				String.format(java.util.Locale.ROOT, "010%08d", id), id);
		if (withProfile) {
			jdbc.update("""
					insert into student_profile(id, birth_date, nationality, current_region, korean_level)
					values (?, '2000-03-14', 'VN', '서울 마포구', 'INTERMEDIATE')
					""", id);
		}
		return id;
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> create(String token, String json) throws Exception {
		return send("POST", CAREER_PATH, token, json);
	}

	private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
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
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private void assertNoCareers() {
		assertThat(jdbc.queryForObject("select count(*) from student_career", Integer.class)).isZero();
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
