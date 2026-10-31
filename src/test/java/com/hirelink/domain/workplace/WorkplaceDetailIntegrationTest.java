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
class WorkplaceDetailIntegrationTest {

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
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void returnsOwnDetailWithFormattedNumberAndNoFileInformationWithoutChangingData() throws Exception {
		long userId = createUser("EMPLOYER");
		long workplaceId = createWorkplace(userId);
		Map<String, Object> memberBefore = jdbc.queryForMap("select * from user_account where id = ?", userId);
		for (String number : new String[] {"0123456789", "012-34-56789"}) {
			jdbc.update("update workplace set business_registration_number = ? where id = ?", number, workplaceId);
			Map<String, Object> workplaceBefore = jdbc.queryForMap("select * from workplace where id = ?", workplaceId);
			HttpResponse<String> response = get(token(userId, "EMPLOYER"), Long.toString(workplaceId));
			assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
			assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
			JsonNode body = mapper.readTree(response.body());
			assertThat(body.propertyNames()).containsExactlyInAnyOrder("workplaceId", "workplaceName", "representativeName",
					"businessRegistrationNumber", "businessTypeId", "businessTypeName", "employeeCountRange",
					"workplaceAddress", "latitude", "longitude");
			assertThat(body.path("workplaceId").asLong()).isEqualTo(workplaceId);
			assertThat(body.path("workplaceName").asString()).isEqualTo("테스트 사업장");
			assertThat(body.path("representativeName").asString()).isEqualTo("등록증 대표자");
			assertThat(body.path("businessRegistrationNumber").asString()).isEqualTo("012-34-56789");
			assertThat(body.path("businessTypeId").asLong()).isEqualTo(8L);
			assertThat(body.path("businessTypeName").asString()).isEqualTo("OTHER");
			assertThat(body.path("employeeCountRange").asString()).isEqualTo("FIVE_OR_MORE");
			assertThat(body.path("workplaceAddress").asString()).isEqualTo("테스트 주소");
			assertThat(body.path("latitude").isNumber()).isTrue();
			assertThat(body.path("latitude").asDouble()).isEqualTo(37.1234567);
			assertThat(body.path("longitude").isNumber()).isTrue();
			assertThat(body.path("longitude").asDouble()).isEqualTo(126.1234567);
			assertThat(jdbc.queryForMap("select * from workplace where id = ?", workplaceId)).isEqualTo(workplaceBefore);
		}
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(memberBefore);
	}

	@Test
	void rejectsOtherOwnersAndReturnsNotFoundForMissingWorkplaces() throws Exception {
		long ownerId = createUser("EMPLOYER");
		long otherId = createUser("EMPLOYER");
		long workplaceId = createWorkplace(ownerId);
		List<Map<String, Object>> before = jdbc.queryForList("select * from workplace order by id");
		assertError(get(token(otherId, "EMPLOYER"), Long.toString(workplaceId)), 403, "FORBIDDEN");
		assertError(get(token(otherId, "EMPLOYER"), workplaceId + "?employerUserId=" + ownerId), 403, "FORBIDDEN");
		for (String missingId : new String[] {"999", "0", "-1"}) {
			HttpResponse<String> response = get(token(ownerId, "EMPLOYER"), missingId);
			assertError(response, 404, "WORKPLACE_NOT_FOUND");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("사업장 정보를 찾을 수 없습니다.");
		}
		assertThat(jdbc.queryForList("select * from workplace order by id")).isEqualTo(before);
	}

	@Test
	void rejectsIdsThatCannotBeConvertedToLong() throws Exception {
		String token = token(createUser("EMPLOYER"), "EMPLOYER");
		for (String id : new String[] {"abc", "1.5", "9223372036854775808"}) {
			assertError(get(token, id), 400, "INVALID_INPUT");
		}
	}

	@Test
	void requiresAuthenticationAndCurrentEmployerRoleAndRejectsWithdrawnAccounts() throws Exception {
		long userId = createUser("STUDENT");
		String workplaceId = Long.toString(createWorkplace(userId));
		assertError(get(null, workplaceId), 401, "UNAUTHORIZED");
		assertError(get("invalid-token", workplaceId), 401, "UNAUTHORIZED");
		assertError(get(token(999L, "EMPLOYER"), workplaceId), 401, "UNAUTHORIZED");
		assertError(get(token(userId, "STUDENT"), workplaceId), 403, "FORBIDDEN");
		assertError(get(token(userId, "EMPLOYER"), workplaceId), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'EMPLOYER', withdrawn_at = now() where id = ?", userId);
		assertError(get(token(userId, "EMPLOYER"), workplaceId), 403, "WITHDRAWN_ACCOUNT");
	}

	@Test
	void invalidStoredDataAndDatabaseFailuresReturnGenericServerError() throws Exception {
		long userId = createUser("EMPLOYER");
		long workplaceId = createWorkplace(userId);
		String token = token(userId, "EMPLOYER");
		jdbc.update("update workplace set business_registration_number = 'invalid-number' where id = ?", workplaceId);
		assertServerError(get(token, Long.toString(workplaceId)));
		jdbc.update("update workplace set business_registration_number = '0123456789' where id = ?", workplaceId);
		Map<String, Object> before = jdbc.queryForMap("select * from workplace where id = ?", workplaceId);
		jdbc.execute("alter table workplace rename column workplace_name to test_unavailable_workplace_name");
		try {
			assertServerError(get(token, Long.toString(workplaceId)));
		} finally {
			jdbc.execute("alter table workplace rename column test_unavailable_workplace_name to workplace_name");
		}
		assertThat(jdbc.queryForMap("select * from workplace where id = ?", workplaceId)).isEqualTo(before);
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
				values (?, '테스트 사업장', '등록증 대표자', 'test-documents/certificate.pdf',
				'0123456789', 8, '테스트 주소', 37.1234567, 126.1234567, 'FIVE_OR_MORE') returning id
				""", Long.class, userId);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> get(String token, String path) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/workplaces/" + path))
				.timeout(Duration.ofSeconds(15)).GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private void assertServerError(HttpResponse<String> response) {
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
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
