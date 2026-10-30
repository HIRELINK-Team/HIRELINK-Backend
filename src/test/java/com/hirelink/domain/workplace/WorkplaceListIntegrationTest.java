package com.hirelink.domain.workplace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
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
class WorkplaceListIntegrationTest {

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
	void clearDatabase() {
		jdbc.execute("TRUNCATE TABLE employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void returnsOnlyOwnWorkplacesInIdOrderWithExactlyTheSummaryFields() throws Exception {
		long userId = createUser("EMPLOYER");
		long otherId = createUser("EMPLOYER");
		createWorkplace(11, userId, 8, "가나다 사업장", "FIVE_OR_MORE");
		createWorkplace(9, otherId, 2, "다른 사업주의 사업장", "UNDER_5");
		createWorkplace(8, userId, 1, "하늘 카페", "UNDER_5");
		List<Map<String, Object>> before = jdbc.queryForList("select * from workplace order by id");
		Map<String, Object> memberBefore = jdbc.queryForMap("select * from user_account where id = ?", userId);

		for (String query : new String[] {"", "?employerUserId=" + otherId}) {
			HttpResponse<String> response = get(token(userId, "EMPLOYER"), query);
			assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
			assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
			JsonNode body = mapper.readTree(response.body());
			assertThat(body.propertyNames()).containsExactly("workplaces");
			JsonNode workplaces = body.path("workplaces");
			assertThat(workplaces.isArray()).isTrue();
			assertThat(workplaces.size()).isEqualTo(2);
			assertWorkplace(workplaces.get(0), 8, "하늘 카페", 1, "FOOD_CAFE", "UNDER_5");
			assertWorkplace(workplaces.get(1), 11, "가나다 사업장", 8, "OTHER", "FIVE_OR_MORE");
		}
		assertThat(jdbc.queryForList("select * from workplace order by id")).isEqualTo(before);
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(memberBefore);
	}

	@Test
	void returnsEmptyListWithoutCreatingDataWhenOnlyAnotherEmployerHasWorkplaces() throws Exception {
		long userId = createUser("EMPLOYER");
		long otherId = createUser("EMPLOYER");
		createWorkplace(8, otherId, 1, "다른 사업주의 사업장", "UNDER_5");
		List<Map<String, Object>> before = jdbc.queryForList("select * from workplace order by id");

		HttpResponse<String> response = get(token(userId, "EMPLOYER"), "");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(mapper.readTree(response.body())).isEqualTo(mapper.readTree("{\"workplaces\":[]}"));
		assertThat(jdbc.queryForList("select * from workplace order by id")).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from employer_profile", Integer.class)).isZero();
	}

	@Test
	void requiresAuthenticationAndCurrentEmployerRoleAndRejectsWithdrawnAccounts() throws Exception {
		assertError(get(null, ""), 401, "UNAUTHORIZED");
		assertError(get("invalid-token", ""), 401, "UNAUTHORIZED");
		assertError(get(token(999L, "EMPLOYER"), ""), 401, "UNAUTHORIZED");
		long userId = createUser("STUDENT");
		createWorkplace(8, userId, 1, "테스트 사업장", "UNDER_5");
		assertError(get(token(userId, "STUDENT"), ""), 403, "FORBIDDEN");
		assertError(get(token(userId, "EMPLOYER"), ""), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'EMPLOYER', withdrawn_at = now() where id = ?", userId);
		assertError(get(token(userId, "EMPLOYER"), ""), 403, "WITHDRAWN_ACCOUNT");
	}

	@Test
	void databaseFailureReturnsGenericServerError() throws Exception {
		long userId = createUser("EMPLOYER");
		createWorkplace(8, userId, 1, "테스트 사업장", "UNDER_5");
		List<Map<String, Object>> before = jdbc.queryForList("select * from workplace order by id");
		jdbc.execute("alter table workplace rename column workplace_name to test_unavailable_workplace_name");
		try {
			HttpResponse<String> response = get(token(userId, "EMPLOYER"), "");
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
		} finally {
			jdbc.execute("alter table workplace rename column test_unavailable_workplace_name to workplace_name");
		}
		assertThat(jdbc.queryForList("select * from workplace order by id")).isEqualTo(before);
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private void createWorkplace(long id, long userId, long businessTypeId, String name, String employeeCountRange) {
		jdbc.update("""
				insert into workplace(id, employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, ?, ?, '테스트 대표자', ?, ?, ?, '테스트 주소', 37.5665000, 126.9780000, ?)
				""", id, userId, name, "test-documents/" + id + ".pdf",
				String.format(Locale.ROOT, "%010d", id), businessTypeId, employeeCountRange);
	}

	private void assertWorkplace(JsonNode workplace, long id, String name, long businessTypeId,
			String businessTypeName, String employeeCountRange) {
		assertThat(workplace.propertyNames()).containsExactlyInAnyOrder("workplaceId", "workplaceName",
				"businessTypeId", "businessTypeName", "workplaceAddress", "employeeCountRange");
		assertThat(workplace.path("workplaceId").asLong()).isEqualTo(id);
		assertThat(workplace.path("workplaceName").asString()).isEqualTo(name);
		assertThat(workplace.path("businessTypeId").asLong()).isEqualTo(businessTypeId);
		assertThat(workplace.path("businessTypeName").asString()).isEqualTo(businessTypeName);
		assertThat(workplace.path("workplaceAddress").asString()).isEqualTo("테스트 주소");
		assertThat(workplace.path("employeeCountRange").asString()).isEqualTo(employeeCountRange);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> get(String token, String query) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
				+ "/api/employers/me/workplaces" + query)).timeout(Duration.ofSeconds(15)).GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
