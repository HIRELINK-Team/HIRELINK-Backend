package com.hirelink.domain.auth;

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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import com.hirelink.domain.auth.service.SmsSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PhoneVerificationIntegrationTest {

	private static final String PHONE = "01000000000";
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
	@Autowired
	private PasswordEncoder passwordEncoder;
	@MockitoSpyBean
	private SmsSender smsSender;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE TABLE phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
		doNothing().when(smsSender).sendVerificationCode(anyString(), anyString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void requestStoresOnlyHashAndReturnsExpiry(String role) throws Exception {
		String token = createUser(role);
		Instant before = Instant.now();
		HttpResponse<String> response = request(token, "010-0000-0000");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(response.body()).size()).isEqualTo(2);
		assertThat(mapper.readTree(response.body()).path("expiresIn").asLong()).isEqualTo(300);
		assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("인증번호가 전송되었습니다.");
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		ArgumentCaptor<String> number = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(smsSender).sendVerificationCode(number.capture(), code.capture());
		assertThat(number.getValue()).isEqualTo(PHONE);
		assertThat(code.getValue()).matches("[0-9]{6}");
		Map<String, Object> stored = jdbc.queryForMap("select * from phone_verification");
		assertThat(stored.get("phone_number")).isEqualTo(PHONE);
		assertThat(passwordEncoder.matches(code.getValue(), (String) stored.get("code_hash"))).isTrue();
		assertThat(stored.get("code_hash")).isNotEqualTo(code.getValue());
		Instant expiresAt = jdbc.queryForObject("select expires_at from phone_verification", java.sql.Timestamp.class).toInstant();
		assertThat(expiresAt).isBetween(before.plusSeconds(300), Instant.now().plusSeconds(300));
		assertThat(jdbc.queryForObject("select phone_number from user_account", String.class)).isNull();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "01112345678", "0101234567", "010123456789", "010-12345678", "+821012345678", "010abcdefgh"})
	void rejectsInvalidPhone(String phone) throws Exception {
		String token = createUser("STUDENT");
		assertError(request(token, phone), 400, "INVALID_INPUT");
		verify(smsSender, never()).sendVerificationCode(anyString(), anyString());
	}

	@Test
	void requiresAccessTokenAndExistingActiveAccount() throws Exception {
		assertError(request(null, PHONE), 401, "UNAUTHORIZED");
		assertError(request("invalid", PHONE), 401, "UNAUTHORIZED");
		String token = createUser("STUDENT");
		jdbc.update("update user_account set withdrawn_at = now()");
		assertError(request(token, PHONE), 403, "WITHDRAWN_ACCOUNT");
		jdbc.update("delete from user_account");
		assertError(request(token, PHONE), 401, "UNAUTHORIZED");
	}

	@Test
	void resendReplacesHashButPreservesFailures() throws Exception {
		String token = createUser("STUDENT");
		assertThat(request(token, PHONE).statusCode()).isEqualTo(200);
		String firstHash = jdbc.queryForObject("select code_hash from phone_verification", String.class);
		assertError(request(token, PHONE), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		ageRequests(61);
		jdbc.update("update phone_verification set failed_attempts = 3");
		assertThat(request(token, PHONE).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from phone_verification", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select code_hash from phone_verification", String.class)).isNotEqualTo(firstHash);
		assertThat(jdbc.queryForObject("select failed_attempts from phone_verification", Integer.class)).isEqualTo(3);
		verify(smsSender, times(2)).sendVerificationCode(anyString(), anyString());
	}

	@Test
	void hourlyLimitFollowsAccountAcrossPhoneNumbers() throws Exception {
		String token = createUser("STUDENT");
		for (int i = 0; i < 5; i++) {
			assertThat(request(token, "0100000000" + i).statusCode()).isEqualTo(200);
			ageRequests(61);
		}
		assertError(request(token, "01000000009"), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		ageRequests(3600);
		assertThat(request(token, "01000000009").statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from phone_verification_send_attempt", Integer.class)).isEqualTo(1);
	}

	@Test
	void hourlyLimitFollowsPhoneAcrossAccounts() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(request(createUser("STUDENT"), PHONE).statusCode()).isEqualTo(200);
			ageRequests(61);
		}
		assertError(request(createUser("EMPLOYER"), PHONE), 429, "PHONE_VERIFICATION_RATE_LIMIT");
	}

	@Test
	void failedVerificationBlocksResendAndPhoneChangesUntilExpiry() throws Exception {
		String token = createUser("STUDENT");
		assertThat(request(token, PHONE).statusCode()).isEqualTo(200);
		ageRequests(61);
		jdbc.update("update phone_verification set failed_attempts = 5");
		assertError(request(token, PHONE), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		assertError(request(token, "01000000001"), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		jdbc.update("update phone_verification set expires_at = now() - interval '1 second'");
		assertThat(request(token, PHONE).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select failed_attempts from phone_verification", Integer.class)).isZero();
	}

	@Test
	void unconfiguredSenderFailsAndStillLimitsRetries() throws Exception {
		String token = createUser("STUDENT");
		doCallRealMethod().when(smsSender).sendVerificationCode(anyString(), anyString());
		assertError(request(token, PHONE), 500, "SMS_DELIVERY_FAILED");
		assertThat(jdbc.queryForObject("select count(*) from phone_verification", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from phone_verification_send_attempt", Integer.class)).isEqualTo(1);
		assertError(request(token, PHONE), 429, "PHONE_VERIFICATION_RATE_LIMIT");
	}

	@Test
	void failedResendDoesNotReplacePreviousCode() throws Exception {
		String token = createUser("STUDENT");
		assertThat(request(token, PHONE).statusCode()).isEqualTo(200);
		Map<String, Object> before = jdbc.queryForMap("select * from phone_verification");
		ageRequests(61);
		doCallRealMethod().when(smsSender).sendVerificationCode(anyString(), anyString());
		assertError(request(token, PHONE), 500, "SMS_DELIVERY_FAILED");
		assertThat(jdbc.queryForMap("select * from phone_verification")).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void simultaneousRequestsRespectAccountAndPhoneCooldown(boolean sameAccount) throws Exception {
		String first = createUser("STUDENT");
		String second = sameAccount ? first : createUser("EMPLOYER");
		HttpRequest firstRequest = httpRequest(first, PHONE);
		HttpRequest secondRequest = httpRequest(second, sameAccount ? "01000000001" : PHONE);
		CompletableFuture<HttpResponse<String>> one = client.sendAsync(firstRequest, HttpResponse.BodyHandlers.ofString());
		CompletableFuture<HttpResponse<String>> two = client.sendAsync(secondRequest, HttpResponse.BodyHandlers.ofString());
		assertThat(List.of(one.get(15, TimeUnit.SECONDS).statusCode(), two.get(15, TimeUnit.SECONDS).statusCode()))
				.containsExactlyInAnyOrder(200, 429);
		verify(smsSender).sendVerificationCode(anyString(), anyString());
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

	private HttpResponse<String> request(String token, String phone) throws Exception {
		return client.send(httpRequest(token, phone), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest httpRequest(String token, String phone) {
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("phoneNumber", phone);
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/phone-verifications"))
				.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
		if (token != null) {
			builder.header("Authorization", "Bearer " + token);
		}
		return builder.build();
	}

	private void ageRequests(int seconds) {
		jdbc.update("update phone_verification_send_attempt set requested_at = requested_at - (? * interval '1 second')", seconds);
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
