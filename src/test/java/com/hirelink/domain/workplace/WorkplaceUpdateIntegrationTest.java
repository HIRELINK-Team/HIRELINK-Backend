package com.hirelink.domain.workplace;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
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
class WorkplaceUpdateIntegrationTest {

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
	void clearDatabase() {
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void updatesOnlyRequestedFieldsAndReturnsDetailWithoutFileInformation() throws Exception {
		long userId = createUser("EMPLOYER");
		long id = createWorkplace(userId);
		String token = token(userId, "EMPLOYER");
		Map<String, Object> expected = new LinkedHashMap<>(workplace(id));
		Map<String, Object> memberBefore = jdbc.queryForMap("select * from user_account where id = ?", userId);

		HttpResponse<String> response = patch(id, token, "{\"workplaceName\":\"수정된 사업장\"}");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		expected.put("workplace_name", "수정된 사업장");
		assertThat(workplace(id)).isEqualTo(expected);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("workplaceId", "workplaceName", "representativeName",
				"businessRegistrationNumber", "businessTypeId", "businessTypeName", "employeeCountRange",
				"workplaceAddress", "latitude", "longitude");
		assertThat(body.path("workplaceId").asLong()).isEqualTo(id);
		assertThat(body.path("workplaceName").asString()).isEqualTo("수정된 사업장");
		assertThat(body.path("representativeName").asString()).isEqualTo("테스트 대표자");
		assertThat(body.path("businessRegistrationNumber").asString()).isEqualTo("012-34-56789");
		assertThat(body.path("employeeCountRange").asString()).isEqualTo("UNDER_5");

		response = patch(id, token, "{\"businessTypeId\":8}");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		expected.put("business_type_id", 8L);
		assertThat(workplace(id)).isEqualTo(expected);
		assertThat(mapper.readTree(response.body()).path("businessTypeName").asString()).isEqualTo("OTHER");

		response = patch(id, token, """
				{"workplaceAddress":"수정된 주소","latitude":37.7654321,"longitude":127.1234567}
				""");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		expected.put("workplace_address", "수정된 주소");
		expected.put("latitude", new BigDecimal("37.7654321"));
		expected.put("longitude", new BigDecimal("127.1234567"));
		assertThat(workplace(id)).isEqualTo(expected);
		HttpResponse<String> detail = client.send(request("GET", Long.toString(id), token, null), HttpResponse.BodyHandlers.ofString());
		assertThat(detail.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(detail.body())).isEqualTo(mapper.readTree(response.body()));
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(memberBefore);
	}

	@Test
	void acceptsMaximumTextLengthsAndCoordinateBoundaries() throws Exception {
		long userId = createUser("EMPLOYER");
		long id = createWorkplace(userId);
		String token = token(userId, "EMPLOYER");
		for (int sign : new int[] {-1, 1}) {
			String json = mapper.writeValueAsString(Map.of("workplaceName", "가".repeat(150), "businessTypeId", 2,
					"workplaceAddress", "나".repeat(500), "latitude", sign * 90, "longitude", sign * 180));
			HttpResponse<String> response = patch(id, token, json);
			assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
			assertThat(workplace(id)).containsEntry("workplace_name", "가".repeat(150))
					.containsEntry("workplace_address", "나".repeat(500)).containsEntry("business_type_id", 2L);
			assertThat((BigDecimal) workplace(id).get("latitude")).isEqualByComparingTo(BigDecimal.valueOf(sign * 90));
			assertThat((BigDecimal) workplace(id).get("longitude")).isEqualByComparingTo(BigDecimal.valueOf(sign * 180));
		}
		assertThat(jdbc.queryForObject("select count(*) from workplace", Integer.class)).isEqualTo(1);
	}

	@Test
	void rejectsInvalidAndUnsupportedFieldsWithoutChangingData() throws Exception {
		long userId = createUser("EMPLOYER");
		long id = createWorkplace(userId);
		String token = token(userId, "EMPLOYER");
		Map<String, Object> before = workplace(id);
		for (String json : List.of("{}", "null", "[]", "{", "{\"workplaceName\":\"\"}", "{\"workplaceName\":\"   \"}",
				"{\"workplaceName\":123}", "{\"workplaceAddress\":\" \"}", "{\"workplaceAddress\":false}",
				"{\"businessTypeId\":\"1\"}", "{\"businessTypeId\":1.5}", "{\"businessTypeId\":9223372036854775808}",
				"{\"latitude\":\"37.1\"}", "{\"longitude\":true}",
				mapper.writeValueAsString(Map.of("workplaceName", "가".repeat(151))),
				mapper.writeValueAsString(Map.of("workplaceAddress", "가".repeat(501))))) {
			assertError(patch(id, token, json), 400, "INVALID_INPUT");
		}
		for (String field : List.of("workplaceName", "businessTypeId", "workplaceAddress", "latitude", "longitude")) {
			assertError(patch(id, token, "{\"" + field + "\":null}"), 400, "INVALID_INPUT");
		}
		for (String field : List.of("representativeName", "businessRegistrationNumber", "employeeCountRange",
				"businessRegistrationFileKey", "employerUserId", "otherJobName", "unknown")) {
			assertError(patch(id, token, mapper.writeValueAsString(Map.of("workplaceName", "변경 시도", field, "test"))),
					400, "INVALID_INPUT");
		}
		for (String latitude : List.of("-90.0000001", "90.0000001", "37.12345678")) {
			assertError(patch(id, token, "{\"workplaceAddress\":\"주소\",\"latitude\":" + latitude + ",\"longitude\":127}"),
					400, "INVALID_INPUT");
		}
		for (String longitude : List.of("-180.0000001", "180.0000001", "127.12345678")) {
			assertError(patch(id, token, "{\"workplaceAddress\":\"주소\",\"latitude\":37,\"longitude\":" + longitude + "}"),
					400, "INVALID_INPUT");
		}
		assertThat(workplace(id)).isEqualTo(before);
	}

	@Test
	void requiresAddressAndBothCoordinatesTogether() throws Exception {
		long userId = createUser("EMPLOYER");
		long id = createWorkplace(userId);
		String token = token(userId, "EMPLOYER");
		Map<String, Object> before = workplace(id);
		for (Map<String, Object> location : List.<Map<String, Object>>of(
				Map.of("workplaceAddress", "새 주소"), Map.of("latitude", 37), Map.of("longitude", 127),
				Map.of("workplaceAddress", "새 주소", "latitude", 37),
				Map.of("workplaceAddress", "새 주소", "longitude", 127), Map.of("latitude", 37, "longitude", 127))) {
			Map<String, Object> request = new LinkedHashMap<>(location);
			request.put("workplaceName", "함께 변경 시도");
			assertError(patch(id, token, mapper.writeValueAsString(request)), 400, "INVALID_INPUT");
		}
		assertThat(workplace(id)).isEqualTo(before);
	}

	@Test
	void rejectsOtherOwnersMissingWorkplacesAndBusinessTypesAndInvalidPathIds() throws Exception {
		long ownerId = createUser("EMPLOYER");
		long otherId = createUser("EMPLOYER");
		long id = createWorkplace(ownerId);
		String token = token(ownerId, "EMPLOYER");
		String update = "{\"workplaceName\":\"변경 시도\"}";
		Map<String, Object> before = workplace(id);
		assertError(patch(id, token(otherId, "EMPLOYER"), update), 403, "FORBIDDEN");
		for (long missingId : new long[] {999, 0, -1}) {
			assertError(patch(missingId, token, update), 404, "WORKPLACE_NOT_FOUND");
			assertError(patch(id, token, "{\"workplaceName\":\"변경 시도\",\"businessTypeId\":" + missingId + "}"),
					404, "BUSINESS_TYPE_NOT_FOUND");
		}
		for (String pathId : List.of("abc", "1.5", "9223372036854775808")) {
			assertError(client.send(request("PATCH", pathId, token, update), HttpResponse.BodyHandlers.ofString()),
					400, "INVALID_INPUT");
		}
		assertThat(workplace(id)).isEqualTo(before);
	}

	@Test
	void requiresAuthenticationAndCurrentEmployerRoleAndRejectsWithdrawnAccounts() throws Exception {
		long userId = createUser("STUDENT");
		long id = createWorkplace(userId);
		String update = "{\"workplaceName\":\"변경 시도\"}";
		Map<String, Object> before = workplace(id);
		assertError(patch(id, null, update), 401, "UNAUTHORIZED");
		assertError(patch(id, "invalid-token", update), 401, "UNAUTHORIZED");
		assertError(patch(id, token(999, "EMPLOYER"), update), 401, "UNAUTHORIZED");
		assertError(patch(id, token(userId, "STUDENT"), update), 403, "FORBIDDEN");
		assertError(patch(id, token(userId, "EMPLOYER"), update), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'EMPLOYER', withdrawn_at = now() where id = ?", userId);
		assertError(patch(id, token(userId, "EMPLOYER"), update), 403, "WITHDRAWN_ACCOUNT");
		assertThat(workplace(id)).isEqualTo(before);
	}

	@Test
	void preservesDifferentFieldsWhenTwoRequestsUpdateSameWorkplace() throws Exception {
		long userId = createUser("EMPLOYER");
		long id = createWorkplace(userId);
		String token = token(userId, "EMPLOYER");
		Map<String, Object> expected = new LinkedHashMap<>(workplace(id));
		CompletableFuture<HttpResponse<String>> nameResponse;
		CompletableFuture<HttpResponse<String>> typeResponse;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from workplace where id = ? for update")) {
				statement.setLong(1, id);
				statement.executeQuery().close();
			}
			try {
				nameResponse = client.sendAsync(request("PATCH", Long.toString(id), token,
						"{\"workplaceName\":\"동시에 수정된 이름\"}"), HttpResponse.BodyHandlers.ofString());
				typeResponse = client.sendAsync(request("PATCH", Long.toString(id), token,
						"{\"businessTypeId\":2}"), HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%workplace%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally {
				connection.rollback();
			}
		}
		assertThat(nameResponse.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(typeResponse.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		expected.put("workplace_name", "동시에 수정된 이름");
		expected.put("business_type_id", 2L);
		assertThat(workplace(id)).isEqualTo(expected);
	}

	@Test
	void rollsBackAllChangesAndReturnsSafeErrorWhenDatabaseWriteFails() throws Exception {
		long userId = createUser("EMPLOYER");
		long id = createWorkplace(userId);
		Map<String, Object> before = workplace(id);
		jdbc.execute("alter table workplace add constraint test_reject_update check (workplace_name <> '저장 실패')");
		try {
			HttpResponse<String> response = patch(id, token(userId, "EMPLOYER"), """
					{"workplaceName":"저장 실패","businessTypeId":8,"workplaceAddress":"새 주소","latitude":38,"longitude":128}
					""");
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(workplace(id)).isEqualTo(before);
		} finally {
			jdbc.execute("alter table workplace drop constraint test_reject_update");
		}
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role, name) values (?, ?, '회원 이름') returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private long createWorkplace(long userId) {
		return jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '기존 사업장', '테스트 대표자', 'test-documents/certificate.pdf',
				'0123456789', 1, '기존 주소', 37.1234567, 126.1234567, 'UNDER_5') returning id
				""", Long.class, userId);
	}

	private Map<String, Object> workplace(long id) {
		return jdbc.queryForMap("select * from workplace where id = ?", id);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> patch(long id, String token, String json) throws Exception {
		return client.send(request("PATCH", Long.toString(id), token, json), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String method, String path, String token, String json) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/workplaces/" + path))
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
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message");
		assertThat(body.path("code").asString()).isEqualTo(code);
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
