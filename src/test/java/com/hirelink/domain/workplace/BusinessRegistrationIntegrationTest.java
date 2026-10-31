package com.hirelink.domain.workplace;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.hirelink.domain.workplace.client.NtsBusinessRegistrationClient;
import com.hirelink.global.exception.BusinessRegistrationLookupException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BusinessRegistrationIntegrationTest {

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
	@MockitoBean
	private NtsBusinessRegistrationClient ntsClient;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearDatabase() {
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void normalizesNumbersAndReturnsStatusWithoutCreatingOrUpdatingData() throws Exception {
		long userId = createUser("EMPLOYER");
		String token = token(userId, "EMPLOYER");
		Map<String, Object> before = jdbc.queryForMap("select * from user_account where id = ?", userId);
		for (boolean valid : new boolean[] {true, false}) {
			when(ntsClient.isActiveBusiness("1234567890")).thenReturn(valid);
			for (String number : new String[] {"1234567890", "123-45-67890", "  123-45-67890  "}) {
				HttpResponse<String> response = post(token, mapper.writeValueAsString(Map.of("businessRegistrationNumber", number)));
				assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
				assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
				JsonNode body = mapper.readTree(response.body());
				assertThat(body.propertyNames()).containsExactlyInAnyOrder("businessRegistrationNumber", "valid", "message");
				assertThat(body.path("businessRegistrationNumber").asString()).isEqualTo("123-45-67890");
				assertThat(body.path("valid").asBoolean()).isEqualTo(valid);
				assertThat(body.path("message").asString()).isEqualTo(valid
						? "확인 가능한 사업자등록번호입니다." : "확인할 수 없는 사업자등록번호입니다.");
			}
		}
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from workplace", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from employer_profile", Integer.class)).isZero();
	}

	@Test
	void invalidInputIsRejectedBeforeExternalLookup() throws Exception {
		String token = token(createUser("EMPLOYER"), "EMPLOYER");
		for (String json : new String[] {"", "null", "[]", "{", "{}", "{\"businessRegistrationNumber\":null}",
				"{\"businessRegistrationNumber\":1234567890}", "{\"businessRegistrationNumber\":true}",
				"{\"businessRegistrationNumber\":[]}", "{\"businessRegistrationNumber\":{}}",
				"{\"businessRegistrationNumber\":\"1234567890\",\"userId\":1}"}) {
			assertError(post(token, json), 400, "INVALID_INPUT");
		}
		for (String number : new String[] {"", "   ", "123456789", "12345678901", "123456789A",
				"１２３４５６７８９０", "123-456-7890", "123-4567890", "12345-67890", "123 45 67890"}) {
			assertError(post(token, mapper.writeValueAsString(Map.of("businessRegistrationNumber", number))), 400, "INVALID_INPUT");
		}
		verifyNoInteractions(ntsClient);
	}

	@Test
	void requiresAuthenticationAndCurrentActiveEmployerAccount() throws Exception {
		String json = "{\"businessRegistrationNumber\":\"1234567890\"}";
		assertError(post(null, json), 401, "UNAUTHORIZED");
		assertError(post("invalid-token", json), 401, "UNAUTHORIZED");
		assertError(post(token(999L, "EMPLOYER"), json), 401, "UNAUTHORIZED");
		long userId = createUser("STUDENT");
		assertError(post(token(userId, "STUDENT"), json), 403, "FORBIDDEN");
		assertError(post(token(userId, "EMPLOYER"), json), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'EMPLOYER', withdrawn_at = now() where id = ?", userId);
		assertError(post(token(userId, "EMPLOYER"), json), 403, "WITHDRAWN_ACCOUNT");
		verifyNoInteractions(ntsClient);
	}

	@Test
	void externalFailureReturnsBadGatewayInsteadOfInvalidBusiness() throws Exception {
		String token = token(createUser("EMPLOYER"), "EMPLOYER");
		when(ntsClient.isActiveBusiness("1234567890")).thenThrow(new BusinessRegistrationLookupException());
		HttpResponse<String> response = post(token, "{\"businessRegistrationNumber\":\"1234567890\"}");
		assertError(response, 502, "BUSINESS_REGISTRATION_LOOKUP_FAILED");
		assertThat(mapper.readTree(response.body()).path("message").asString())
				.isEqualTo("사업자등록 상태를 조회하지 못했습니다. 잠시 후 다시 시도해주세요.");
		verify(ntsClient).isActiveBusiness("1234567890");
	}

	@Test
	void unexpectedFailureReturnsGenericInternalError() throws Exception {
		String token = token(createUser("EMPLOYER"), "EMPLOYER");
		when(ntsClient.isActiveBusiness("1234567890")).thenThrow(new IllegalStateException("test-internal-detail"));
		HttpResponse<String> response = post(token, "{\"businessRegistrationNumber\":\"1234567890\"}");
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> post(String token, String json) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
				+ "/api/workplaces/business-registration/verify")).timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
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
