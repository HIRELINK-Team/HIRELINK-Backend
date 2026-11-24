package com.hirelink.domain.developerinquiry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hirelink.domain.auth.support.LoginSessionFixtures;
import com.hirelink.support.PostgreSQLIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DeveloperInquiryIntegrationTest.TestClockConfiguration.class)
class DeveloperInquiryIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final Instant SUBMITTED_AT = Instant.parse("2026-11-24T03:00:00Z");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@LocalServerPort private int port;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;
	@Autowired private JwtEncoder jwtEncoder;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE developer_inquiry, refresh_token, auth_account, user_account RESTART IDENTITY CASCADE");
	}

	@Test
	void studentCreatesInquiryWithAuthenticatedAccountSnapshot() throws Exception {
		long student = createUser("student@example.com", "STUDENT", false);

		HttpResponse<String> response = create(token(student, "STUDENT"),
				Map.of("inquiryType", "  사용 방법  ", "title", "  제안 확인 문의  ",
						"content", "  채용 제안을 어디서 확인하나요?  "));

		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("inquiryId", "submittedAt");
		assertThat(Instant.parse(body.path("submittedAt").asString())).isEqualTo(SUBMITTED_AT);
		Map<String, Object> saved = jdbc.queryForMap("select * from developer_inquiry where id = ?",
				body.path("inquiryId").asLong());
		assertThat(saved).containsEntry("author_user_id", student)
				.containsEntry("user_role", "STUDENT")
				.containsEntry("reply_email", "student@example.com")
				.containsEntry("inquiry_type", "사용 방법")
				.containsEntry("title", "제안 확인 문의")
				.containsEntry("content", "채용 제안을 어디서 확인하나요?");
		assertThat(((Timestamp)saved.get("submitted_at")).toInstant()).isEqualTo(SUBMITTED_AT);
	}

	@Test
	void employerUsesTheSameApiAndStoresEmployerAccountData() throws Exception {
		long employer = createUser("employer@example.com", "EMPLOYER", false);

		HttpResponse<String> response = create(token(employer, "EMPLOYER"), validRequest());

		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		assertThat(jdbc.queryForMap("select author_user_id, user_role, reply_email from developer_inquiry"))
				.containsEntry("author_user_id", employer)
				.containsEntry("user_role", "EMPLOYER")
				.containsEntry("reply_email", "employer@example.com");
	}

	@Test
	void rejectsMissingBlankAndOversizedFields() throws Exception {
		long student = createUser("student@example.com", "STUDENT", false);
		String accessToken = token(student, "STUDENT");

		assertError(create(accessToken, Map.of("title", "제목", "content", "내용")),
				400, "INVALID_INPUT");
		assertError(create(accessToken, Map.of("inquiryType", "   ", "title", "제목", "content", "내용")),
				400, "INVALID_INPUT");
		assertError(create(accessToken, Map.of("inquiryType", "사용 방법", "title", " ", "content", "내용")),
				400, "INVALID_INPUT");
		assertError(create(accessToken, Map.of("inquiryType", "사용 방법", "title", "제목", "content", " ")),
				400, "INVALID_INPUT");
		assertError(create(accessToken, Map.of("inquiryType", "사용 방법", "title", "제목", "content", "a".repeat(5001))),
				400, "INVALID_INPUT");
		assertThat(inquiryCount()).isZero();
	}

	@Test
	void requiresAuthenticationAndAnActiveExistingAccount() throws Exception {
		long withdrawn = createUser("withdrawn@example.com", "STUDENT", true);

		assertError(create(null, validRequest()), 401, "UNAUTHORIZED");
		assertError(create(token(99999, "STUDENT"), validRequest()), 401, "UNAUTHORIZED");
		assertError(create(token(withdrawn, "STUDENT"), validRequest()), 403, "WITHDRAWN_ACCOUNT");
		assertThat(inquiryCount()).isZero();
	}

	@Test
	void rejectsAccountWithoutReplyEmailInsteadOfSubstitutingAValue() throws Exception {
		long student = createUser(null, "STUDENT", false);

		assertError(create(token(student, "STUDENT"), validRequest()),
				409, "INQUIRY_REPLY_EMAIL_REQUIRED");
		assertThat(inquiryCount()).isZero();
	}

	@Test
	void openApiDocumentsSharedInquiryRequestAndResponses() throws Exception {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		JsonNode operation = mapper.readTree(response.body()).path("paths")
				.path("/api/developer-inquiries").path("post");
		assertThat(operation.path("summary").asString()).isEqualTo("개발자 문의 접수");
		assertThat(operation.path("responses").propertyNames()).contains("201", "400", "401", "403", "409");
		assertThat(operation.path("requestBody").path("content").path("application/json")
				.path("schema").path("$ref").asString())
				.isEqualTo("#/components/schemas/DeveloperInquiryCreateRequest");
		JsonNode requestSchema = mapper.readTree(response.body()).path("components").path("schemas")
				.path("DeveloperInquiryCreateRequest");
		assertThat(requestSchema.path("required")).hasSize(3);
		assertThat(requestSchema.path("properties").path("inquiryType").path("example").asString())
				.isEqualTo("사용 방법");
		assertThat(requestSchema.path("properties").path("inquiryType").has("enum")).isFalse();
	}

	private Map<String, String> validRequest() {
		return Map.of("inquiryType", "사용 방법", "title", "사용 방법 문의", "content", "문의 내용입니다.");
	}

	private long createUser(String email, String role, boolean withdrawn) {
		return jdbc.queryForObject("""
				insert into user_account(email, role, withdrawn_at)
				values (?, ?, case when ? then now() else null end) returning id
				""", Long.class, email, role, withdrawn);
	}

	private HttpResponse<String> create(String token, Map<String, ?> requestBody) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/developer-inquiries"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.claim("sid", LoginSessionFixtures.create(jdbc, userId))
				.issuer("hirelink").subject(Long.toString(userId)).issuedAt(now).notBefore(now)
				.expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private int inquiryCount() {
		return jdbc.queryForObject("select count(*) from developer_inquiry", Integer.class);
	}

	private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo(code);
	}

	private static String createTestSigningKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(SUBMITTED_AT, ZoneOffset.UTC);
		}
	}
}
