package com.hirelink.domain.member;

import com.hirelink.support.PostgreSQLIntegrationTest;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TermAgreementIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
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
		jdbc.execute("TRUNCATE TABLE employment_popup_notification, employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, student_registration_verification, user_account RESTART IDENTITY");
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void savesAgreementsForBothRolesAndReturnsSpecifiedFields(String role) throws Exception {
		assertThat(jdbc.queryForList("select term_type_name from term_type where not is_social_signup order by id", String.class))
				.containsExactly("이용약관", "개인정보처리방침", "광고성 정보 수신", "위치기반 서비스 이용약관");
		String token = createUser(role);
		HttpResponse<String> response = save(token, """
				{"agreements":[{"termTypeId":1,"isAgreed":true},{"termTypeId":2,"isAgreed":false}]}
				""");
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(1);
		assertThat(body.path("agreements").size()).isEqualTo(2);
		JsonNode agreed = body.path("agreements").get(0);
		assertThat(agreed.size()).isEqualTo(3);
		assertThat(agreed.path("termTypeId").asLong()).isEqualTo(1);
		assertThat(agreed.path("isAgreed").asBoolean()).isTrue();
		assertThat(Instant.parse(agreed.path("agreedAt").asString())).isEqualTo(storedAgreedAt(1));
		assertThat(body.path("agreements").get(1).path("isAgreed").asBoolean()).isFalse();
		assertThat(body.path("agreements").get(1).path("agreedAt").isNull()).isTrue();
		assertThat(jdbc.queryForObject("select is_agreed from user_term_agreement where term_type_id = 2", Boolean.class)).isFalse();
		assertThat(storedAgreedAt(2)).isNull();
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
	}

	@Test
	void repeatsPreserveTimestampAndChangedConsentUpdatesIt() throws Exception {
		String token = createUser("EMPLOYER");
		String agree = "{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":true}]}";
		HttpResponse<String> first = save(token, agree);
		assertThat(first.statusCode()).isEqualTo(200);
		Instant firstAgreedAt = storedAgreedAt(1);
		assertThat(save(token, agree).body()).isEqualTo(first.body());
		assertThat(storedAgreedAt(1)).isEqualTo(firstAgreedAt);

		HttpResponse<String> declined = save(token, "{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":false}]}");
		assertThat(declined.statusCode()).isEqualTo(200);
		assertThat(storedAgreedAt(1)).isNull();
		assertThat(save(token, agree).statusCode()).isEqualTo(200);
		assertThat(storedAgreedAt(1)).isAfter(firstAgreedAt);
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Integer.class)).isEqualTo(1);
	}

	@Test
	void updatesOnlyRequestedTermsAndAllowsEmptyListWithoutChanges() throws Exception {
		String token = createUser("STUDENT");
		assertThat(save(token, """
				{"agreements":[{"termTypeId":1,"isAgreed":true},{"termTypeId":2,"isAgreed":true}]}
				""").statusCode()).isEqualTo(200);
		Instant untouched = storedAgreedAt(2);
		HttpResponse<String> response = save(token, "{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":false}]}");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(response.body()).path("agreements").size()).isEqualTo(1);
		assertThat(storedAgreedAt(2)).isEqualTo(untouched);
		List<Map<String, Object>> before = jdbc.queryForList("select * from user_term_agreement order by term_type_id");
		HttpResponse<String> empty = save(token, "{\"agreements\":[]}");
		assertThat(empty.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(empty.body()).path("agreements").isEmpty()).isTrue();
		assertThat(jdbc.queryForList("select * from user_term_agreement order by term_type_id")).isEqualTo(before);
	}

	@Test
	void requiresAuthenticationAndUsesOnlyTokenUser() throws Exception {
		String body = "{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":true}]}";
		assertError(save(null, body), 401, "UNAUTHORIZED");
		String first = createUser("STUDENT");
		String second = createUser("EMPLOYER");
		assertThat(save(first, body).statusCode()).isEqualTo(200);
		HttpResponse<String> response = save(second, """
				{"userId":1,"agreements":[{"termTypeId":1,"isAgreed":false}]}
				""");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select is_agreed from user_term_agreement where user_id = 1", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("select is_agreed from user_term_agreement where user_id = 2", Boolean.class)).isFalse();
		jdbc.update("update user_account set withdrawn_at = now() where id = 2");
		assertError(save(second, body), 403, "WITHDRAWN_ACCOUNT");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{}",
			"{\"agreements\":[null]}",
			"{\"agreements\":[{\"termTypeId\":1}]}",
			"{\"agreements\":[{\"termTypeId\":0,\"isAgreed\":true}]}",
			"{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":true},{\"termTypeId\":1,\"isAgreed\":false}]}"
	})
	void rejectsInvalidInputWithoutSaving(String body) throws Exception {
		assertError(save(createUser("STUDENT"), body), 400, "INVALID_INPUT");
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Integer.class)).isZero();
	}

	@Test
	void unknownTermRejectsWholeRequest() throws Exception {
		String token = createUser("EMPLOYER");
		assertThat(save(token, "{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":false}]}").statusCode()).isEqualTo(200);
		assertError(save(token, """
				{"agreements":[{"termTypeId":1,"isAgreed":true},{"termTypeId":999,"isAgreed":true}]}
				"""), 404, "TERM_TYPE_NOT_FOUND");
		assertThat(jdbc.queryForObject("select is_agreed from user_term_agreement", Boolean.class)).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Integer.class)).isEqualTo(1);
	}

	@Test
	void databaseFailureRollsBackEntireBatch() throws Exception {
		String token = createUser("EMPLOYER");
		// 일회용 테스트 DB에서 두 번째 약관 저장 실패를 발생시킨다.
		jdbc.execute("alter table user_term_agreement add constraint test_storage_failure check (term_type_id <> 2)");
		try {
			assertError(save(token, """
					{"agreements":[{"termTypeId":1,"isAgreed":true},{"termTypeId":2,"isAgreed":true}]}
					"""), 500, "INTERNAL_SERVER_ERROR");
			assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Integer.class)).isZero();
		} finally {
			jdbc.execute("alter table user_term_agreement drop constraint test_storage_failure");
		}
	}

	@Test
	void simultaneousRequestsKeepOneAgreementAndSameTimestamp() throws Exception {
		String token = createUser("EMPLOYER");
		HttpRequest request = httpRequest(token, "{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":true}]}");
		CompletableFuture<HttpResponse<String>> one = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		CompletableFuture<HttpResponse<String>> two = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> first = one.get(15, TimeUnit.SECONDS);
		HttpResponse<String> second = two.get(15, TimeUnit.SECONDS);
		assertThat(first.statusCode()).isEqualTo(200);
		assertThat(second.statusCode()).isEqualTo(200);
		assertThat(first.body()).isEqualTo(second.body());
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Integer.class)).isEqualTo(1);
	}

	@Test
	void locationConsentStartsFalseWithoutCreatingRowsOrRequiringOnboarding() throws Exception {
		String token = createUser("STUDENT");
		long locationId = locationTermId();
		Map<String, Object> term = jdbc.queryForMap("select is_required,is_social_signup,is_student_registration from term_type where id = ?", locationId);
		assertThat(term.values()).containsOnly(false);
		HttpResponse<String> first = locationConsent(token);
		assertLocation(first, locationId, false, null);
		assertThat(locationConsent(token).body()).isEqualTo(first.body());
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from student_profile", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from student_registration_verification", Integer.class)).isZero();
	}

	@Test
	void locationConsentUsesExistingSaveAndPreservesTimestampAcrossLaterVisits() throws Exception {
		String token = createUser("STUDENT");
		long id = locationTermId();
		String agree = "{\"agreements\":[{\"termTypeId\":" + id + ",\"isAgreed\":true}]}";
		assertThat(save(token, "{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":true}]}").statusCode()).isEqualTo(200);
		Instant otherTermTime = storedAgreedAt(1);
		HttpResponse<String> saved = save(token, agree);
		assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
		Instant firstTime = Instant.parse(mapper.readTree(saved.body()).path("agreements").get(0).path("agreedAt").asString());
		assertLocation(locationConsent(token), id, true, firstTime);
		Instant earlier = Instant.parse("2026-01-01T01:02:03Z");
		jdbc.update("update user_term_agreement set agreed_at = ? where term_type_id = ?", java.sql.Timestamp.from(earlier), id);
		assertLocation(locationConsent(token), id, true, earlier);
		assertThat(save(token, agree).statusCode()).isEqualTo(200);
		assertLocation(locationConsent(token), id, true, earlier);
		assertThat(storedAgreedAt(1)).isEqualTo(otherTermTime);
		assertThat(save(token, "{\"agreements\":[{\"termTypeId\":" + id + ",\"isAgreed\":false}]}").statusCode()).isEqualTo(200);
		assertLocation(locationConsent(token), id, false, null);
		assertThat(save(token, agree).statusCode()).isEqualTo(200);
		assertThat(storedAgreedAt(id)).isAfter(earlier);
		assertLocation(locationConsent(token), id, true, storedAgreedAt(id));
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement where term_type_id = ?", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void locationConsentReadsOnlyAuthenticatedMemberAndRejectsWithdrawnOrMissingUsers() throws Exception {
		assertError(locationConsent(null), 401, "UNAUTHORIZED");
		assertError(locationConsent("invalid"), 401, "UNAUTHORIZED");
		String first = createUser("STUDENT");
		String second = createUser("EMPLOYER");
		long id = locationTermId();
		assertThat(save(first, "{\"agreements\":[{\"termTypeId\":" + id + ",\"isAgreed\":true}]}").statusCode()).isEqualTo(200);
		assertLocation(locationConsent(second), id, false, null);
		HttpRequest otherId = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me/term-agreements/location?userId=1"))
				.header("Authorization", "Bearer " + second).GET().build();
		assertLocation(client.send(otherId, HttpResponse.BodyHandlers.ofString()), id, false, null);
		HttpRequest cookieOnly = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me/term-agreements/location"))
				.header("Cookie", "socialSignup=temporary-signup-session").GET().build();
		assertError(client.send(cookieOnly, HttpResponse.BodyHandlers.ofString()), 401, "UNAUTHORIZED");
		jdbc.update("update user_account set withdrawn_at = now() where id = 2");
		assertError(locationConsent(second), 403, "WITHDRAWN_ACCOUNT");
		jdbc.update("delete from user_account where id = 2");
		assertError(locationConsent(second), 401, "UNAUTHORIZED");
		assertLocation(locationConsent(first), id, true, storedAgreedAt(id));
	}

	@Test
	void simultaneousLocationAgreementsKeepOneRowAndOriginalTimestamp() throws Exception {
		String token = createUser("STUDENT");
		long id = locationTermId();
		HttpRequest request = httpRequest(token, "{\"agreements\":[{\"termTypeId\":" + id + ",\"isAgreed\":true}]}");
		var one = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		var two = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> first = one.get(15, TimeUnit.SECONDS);
		HttpResponse<String> second = two.get(15, TimeUnit.SECONDS);
		assertThat(first.statusCode()).isEqualTo(200);
		assertThat(second.statusCode()).isEqualTo(200);
		assertThat(second.body()).isEqualTo(first.body());
		assertLocation(locationConsent(token), id, true, storedAgreedAt(id));
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Integer.class)).isEqualTo(1);
	}

	@Test
	void missingLocationTermReturnsExistingErrorWithoutCreatingConsent() throws Exception {
		String token = createUser("STUDENT");
		long id = locationTermId();
		jdbc.update("update term_type set term_type_name = 'test-location-term' where id = ?", id);
		try {
			assertError(locationConsent(token), 404, "TERM_TYPE_NOT_FOUND");
			assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Integer.class)).isZero();
		} finally {
			jdbc.update("update term_type set term_type_name = '위치기반 서비스 이용약관' where id = ?", id);
		}
	}

	@Test
	void locationOpenApiMatchesLookupAndExistingSaveContract() throws Exception {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode document = mapper.readTree(response.body());
		JsonNode operation = document.path("paths").path("/api/users/me/term-agreements/location").path("get");
		for (String status : List.of("200", "401", "403", "404", "500")) {
			assertThat(operation.path("responses").path(status).path("content").has("application/json")).isTrue();
		}
		JsonNode schema = document.path("components").path("schemas").path("LocationConsentResponse");
		assertThat(schema.path("required").toString()).contains("termTypeId", "termTypeName", "isAgreed", "agreedAt");
		assertThat(schema.path("properties").size()).isEqualTo(4);
		assertThat(schema.path("properties").path("agreedAt").toString()).contains("null");
		assertThat(document.path("paths").path("/api/users/me/term-agreements").path("put").path("description").asString()).contains("/api/users/me/term-agreements/location");
	}

	private long locationTermId() {
		return jdbc.queryForObject("select id from term_type where term_type_name = '위치기반 서비스 이용약관'", Long.class);
	}

	private HttpResponse<String> locationConsent(String token) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me/term-agreements/location")).GET();
		if (token != null) { request.header("Authorization", "Bearer " + token); }
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private void assertLocation(HttpResponse<String> response, long id, boolean agreed, Instant agreedAt) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(4);
		assertThat(body.path("termTypeId").asLong()).isEqualTo(id);
		assertThat(body.path("termTypeName").asString()).isEqualTo("위치기반 서비스 이용약관");
		assertThat(body.path("isAgreed").asBoolean()).isEqualTo(agreed);
		assertThat(body.has("agreedAt")).isTrue();
		if (agreedAt == null) { assertThat(body.path("agreedAt").isNull()).isTrue(); }
		else { assertThat(Instant.parse(body.path("agreedAt").asString())).isEqualTo(agreedAt); }
	}

	private String createUser(String role) {
		Long userId = jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(userId.toString())
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private Instant storedAgreedAt(long termTypeId) {
		java.sql.Timestamp timestamp = jdbc.queryForObject(
				"select agreed_at from user_term_agreement where term_type_id = ?", java.sql.Timestamp.class, termTypeId);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private HttpResponse<String> save(String token, String body) throws Exception {
		return client.send(httpRequest(token, body), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest httpRequest(String token, String body) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me/term-agreements"))
				.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			builder.header("Authorization", "Bearer " + token);
		}
		return builder.build();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo(code);
		assertThat(mapper.readTree(response.body()).size()).isEqualTo(2);
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
