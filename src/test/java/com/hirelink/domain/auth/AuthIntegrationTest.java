package com.hirelink.domain.auth;

import java.net.URI;
import java.net.HttpCookie;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.RefreshToken;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.repository.RefreshTokenRepository;
import com.hirelink.domain.member.repository.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthIntegrationTest.TestAuthController.class)
class AuthIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@MockitoSpyBean
	private UserAccountRepository userAccountRepository;

	@MockitoSpyBean
	private AuthAccountRepository authAccountRepository;

	@MockitoSpyBean
	private RefreshTokenRepository refreshTokenRepository;

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		// DynamicPropertySource가 연결한 일회용 PostgreSQL 컨테이너만 초기화한다.
		jdbcTemplate.execute("TRUNCATE TABLE student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void signupStoresBothAccountsAndReturnsOnlyPublicFields(String role) throws Exception {
		HttpResponse<String> response = signup("  Member@Example.COM  ", "password123", role);
		assertThat(response.statusCode()).isEqualTo(201);
		JsonNode body = objectMapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(6);
		assertThat(body.path("userId").asLong()).isPositive();
		assertThat(body.path("email").asString()).isEqualTo("member@example.com");
		assertThat(body.path("role").asString()).isEqualTo(role);
		assertThat(response.headers().firstValue("Authorization")).isEmpty();
		assertThat(body.has("refreshToken")).isFalse();
		assertThat(body.path("tokenType").asString()).isEqualTo("Bearer");
		assertThat(body.path("expiresIn").asLong()).isEqualTo(3600);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertTokenStorage(response);

		Map<String, Object> user = jdbcTemplate.queryForMap("SELECT * FROM user_account");
		assertThat(user.get("id")).isEqualTo(body.path("userId").asLong());
		assertThat(user.get("email")).isEqualTo("member@example.com");
		assertThat(user.get("role")).isEqualTo(role);
		assertThat(user.get("preferred_language")).isEqualTo("KO");
		assertThat(user.get("created_at")).isNotNull();
		assertThat(user.get("updated_at")).isEqualTo(user.get("created_at"));
		for (String nullableColumn : new String[]{"name", "email_verified_at", "phone_number",
				"phone_verified_at", "acquisition_channel", "withdrawn_at"}) {
			assertThat(user).containsEntry(nullableColumn, null);
		}

		Map<String, Object> auth = jdbcTemplate.queryForMap("SELECT * FROM auth_account");
		assertThat(auth.get("user_id")).isEqualTo(user.get("id"));
		assertThat(auth.get("provider")).isEqualTo("LOCAL");
		assertThat(auth.get("provider_user_id")).isEqualTo("member@example.com");
		String passwordHash = (String) auth.get("password_hash");
		assertThat(passwordHash).startsWith("$2").isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", passwordHash)).isTrue();
	}

	@ParameterizedTest
	@MethodSource("invalidRequests")
	void invalidInputReturns400WithoutCreatingAccounts(String json) throws Exception {
		assertError(post(json), 400, "INVALID_INPUT");
		assertAccountCounts(0, 0);
	}

	static Stream<String> invalidRequests() {
		return Stream.of(
				"{}",
				"null",
				"{",
				"",
				"{\"email\":\"invalid\",\"password\":\"password123\",\"role\":\"STUDENT\"}",
				"{\"email\":\"  \",\"password\":\"password123\",\"role\":\"STUDENT\"}",
				"{\"email\":\"member@example.com\",\"password\":\"abc1234\",\"role\":\"STUDENT\"}",
				"{\"email\":\"member@example.com\",\"password\":\"abcdefgh\",\"role\":\"STUDENT\"}",
				"{\"email\":\"member@example.com\",\"password\":\"12345678\",\"role\":\"STUDENT\"}",
				"{\"email\":\"member@example.com\",\"password\":null,\"role\":\"STUDENT\"}",
				"{\"email\":\"member@example.com\",\"password\":\"password123\",\"role\":\"ADMIN\"}",
				"{\"email\":\"member@example.com\",\"password\":\"password123\",\"role\":\"student\"}",
				"{\"email\":\"member@example.com\",\"password\":\"password123\",\"role\":null}",
				"{\"email\":\"member@example.com\",\"password\":\"password123\",\"role\":0}"
		);
	}

	@Test
	void normalizedDuplicateEmailReturns409() throws Exception {
		assertThat(signup("member@example.com", "password123", "STUDENT").statusCode()).isEqualTo(201);
		HttpResponse<String> response = signup("  MEMBER@EXAMPLE.COM  ", "password456", "EMPLOYER");
		assertError(response, 409, "DUPLICATE_EMAIL");
		assertThat(objectMapper.readTree(response.body()).path("message").asString())
				.isEqualTo("이미 사용 중인 이메일입니다.");
		assertAccountCounts(1, 1);
	}

	@Test
	void concurrentSignupReturnsOneSuccessAndOneConflict() throws Exception {
		CyclicBarrier bothEmailsChecked = new CyclicBarrier(2);
		doAnswer(invocation -> {
			// 두 요청 모두 사전 중복 조회를 통과한 상황을 만들고 실제 DB UNIQUE 처리를 검증한다.
			bothEmailsChecked.await(10, TimeUnit.SECONDS);
			return false;
		}).when(userAccountRepository).existsByEmail("race@example.com");

		HttpRequest request = signupRequest(signupJson("race@example.com", "password123", "STUDENT"));
		var first = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		var second = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> firstResponse = first.get(20, TimeUnit.SECONDS);
		HttpResponse<String> secondResponse = second.get(20, TimeUnit.SECONDS);
		assertThat(new int[]{firstResponse.statusCode(), secondResponse.statusCode()})
				.containsExactlyInAnyOrder(201, 409);
		assertError(firstResponse.statusCode() == 409 ? firstResponse : secondResponse, 409, "DUPLICATE_EMAIL");
		assertAccountCounts(1, 1);
	}

	@Test
	void authAccountUniqueViolationRollsBackNewUserAccount() throws Exception {
		jdbcTemplate.update("INSERT INTO user_account (email, role) VALUES (?, ?)", "existing@example.com", "STUDENT");
		Long existingUserId = jdbcTemplate.queryForObject("SELECT id FROM user_account", Long.class);
		jdbcTemplate.update("INSERT INTO auth_account (user_id, provider, provider_user_id) VALUES (?, ?, ?)",
				existingUserId, "LOCAL", "conflict@example.com");

		assertError(signup("conflict@example.com", "password123", "STUDENT"), 409, "DUPLICATE_EMAIL");
		assertThat(userAccountRepository.existsByEmail("conflict@example.com")).isFalse();
		assertAccountCounts(1, 1);
	}

	@Test
	void unexpectedAuthSaveFailureRollsBackUserAndReturnsSafe500() throws Exception {
		doThrow(new IllegalStateException("internal database detail"))
				.when(authAccountRepository).saveAndFlush(any(AuthAccount.class));
		HttpResponse<String> response = signup("rollback@example.com", "password123", "STUDENT");
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(response.body()).doesNotContain("internal database detail", "password123", "Exception");
		assertAccountCounts(0, 0);
	}

	@Test
	void bcryptRejectedInputIsNotTruncatedOrStored() throws Exception {
		assertError(signup("long@example.com", "a".repeat(72) + "1", "STUDENT"), 400, "INVALID_PASSWORD");
		assertAccountCounts(0, 0);
	}

	@Test
	void passwordIsNotTrimmed() throws Exception {
		String password = " password123 ";
		assertThat(signup("spaces@example.com", password, "STUDENT").statusCode()).isEqualTo(201);
		String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM auth_account", String.class);
		assertThat(passwordEncoder.matches(password, hash)).isTrue();
		assertThat(passwordEncoder.matches(password.trim(), hash)).isFalse();
	}

	@Test
	void protectedPathRequiresAuthenticationWithoutRedirectingToLogin() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/not-existing"))
				.timeout(Duration.ofSeconds(10)).GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertError(response, 401, "UNAUTHORIZED");
		assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
		assertThat(response.headers().firstValue("Location")).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void loginReturnsTokensAndAuthenticatesRequests(String role) throws Exception {
		assertThat(signup("member@example.com", "password123", role).statusCode()).isEqualTo(201);
		HttpResponse<String> response = login(" MEMBER@EXAMPLE.COM ", "password123");
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = objectMapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(5);
		assertThat(body.path("role").asString()).isEqualTo(role);
		assertThat(body.path("tokenType").asString()).isEqualTo("Bearer");
		assertThat(body.path("expiresIn").asLong()).isEqualTo(3600);
		assertThat(body.has("refreshToken")).isFalse();
		assertThat(body.has("email")).isFalse();
		assertTokenStorage(response);

		HttpResponse<String> authenticated = authenticatedRequest(body.path("accessToken").asString(), "GET");
		assertThat(authenticated.statusCode()).isEqualTo(200);
		JsonNode currentUser = objectMapper.readTree(authenticated.body());
		assertThat(currentUser.path("userId").asLong()).isEqualTo(body.path("userId").asLong());
		assertThat(currentUser.path("role").asString()).isEqualTo(role);
		assertThat(authenticatedRequest(body.path("accessToken").asString(), "POST").statusCode()).isEqualTo(200);
	}

	@Test
	void loginDoesNotTrimPassword() throws Exception {
		signup("spaces@example.com", " password123 ", "STUDENT");
		assertThat(login("spaces@example.com", " password123 ").statusCode()).isEqualTo(200);
		assertError(login("spaces@example.com", "password123"), 401, "INVALID_CREDENTIALS");
	}

	@Test
	void incorrectAndUnknownCredentialsHaveSameResponse() throws Exception {
		signup("member@example.com", "password123", "STUDENT");
		HttpResponse<String> wrongPassword = login("member@example.com", "wrong123");
		HttpResponse<String> unknownEmail = login("unknown@example.com", "password123");
		assertError(wrongPassword, 401, "INVALID_CREDENTIALS");
		assertError(unknownEmail, 401, "INVALID_CREDENTIALS");
		assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
		assertThat(wrongPassword.headers().allValues("Set-Cookie")).isEmpty();
		assertError(login("member@example.com", "a".repeat(72) + "1"), 401, "INVALID_CREDENTIALS");
		assertThat(refreshTokenCount()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{", "null", "{\"email\":\"invalid\",\"password\":\"password123\"}",
			"{\"email\":\"member@example.com\",\"password\":\"\"}"})
	void invalidLoginInputReturns400(String json) throws Exception {
		assertError(postTo("/api/auth/login", json), 400, "INVALID_INPUT");
		assertThat(refreshTokenCount()).isZero();
	}

	@Test
	void withdrawnAccountCannotLogIn() throws Exception {
		signup("withdrawn@example.com", "password123", "STUDENT");
		jdbcTemplate.update("UPDATE user_account SET withdrawn_at = now() WHERE email = ?", "withdrawn@example.com");
		assertError(login("withdrawn@example.com", "password123"), 403, "WITHDRAWN_ACCOUNT");
		assertError(login("withdrawn@example.com", "wrong123"), 401, "INVALID_CREDENTIALS");
		assertThat(refreshTokenCount()).isEqualTo(1);
	}

	@Test
	void separateLoginsRemainValidAndDoNotExtendExistingExpiry() throws Exception {
		HttpResponse<String> first = signup("member@example.com", "password123", "STUDENT");
		Map<String, Object> existing = jdbcTemplate.queryForMap("SELECT * FROM refresh_token");
		HttpResponse<String> second = login("member@example.com", "password123");
		assertThat(second.statusCode()).isEqualTo(200);
		assertThat(refreshCookieValue(first)).isNotEqualTo(refreshCookieValue(second));
		assertThat(refreshTokenCount()).isEqualTo(2);
		assertThat(jdbcTemplate.queryForMap("SELECT * FROM refresh_token WHERE id = ?", existing.get("id")))
				.isEqualTo(existing);
		for (HttpResponse<String> response : List.of(first, second)) {
			String accessToken = objectMapper.readTree(response.body()).path("accessToken").asString();
			assertThat(authenticatedRequest(accessToken, "GET").statusCode()).isEqualTo(200);
		}
	}

	@Test
	void refreshTokenCannotBeUsedAsAccessTokenOrCookieAuthentication() throws Exception {
		HttpResponse<String> response = signup("member@example.com", "password123", "STUDENT");
		String refreshToken = refreshCookieValue(response);
		assertError(authenticatedRequest(refreshToken, "GET"), 401, "UNAUTHORIZED");
		HttpRequest cookieRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/test/auth/me"))
				.timeout(Duration.ofSeconds(10)).header("Cookie", "refreshToken=" + refreshToken).GET().build();
		assertError(httpClient.send(cookieRequest, HttpResponse.BodyHandlers.ofString()), 401, "UNAUTHORIZED");
	}

	@Test
	void malformedAndTamperedTokensAreRejected() throws Exception {
		HttpResponse<String> response = signup("member@example.com", "password123", "STUDENT");
		String token = objectMapper.readTree(response.body()).path("accessToken").asString();
		int signatureStart = token.lastIndexOf('.') + 1;
		char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
		String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
		assertError(authenticatedRequest(tampered, "GET"), 401, "UNAUTHORIZED");
		assertError(authenticatedRequest("not-a-token", "GET"), 401, "UNAUTHORIZED");
	}

	@ParameterizedTest
	@ValueSource(strings = {"expired", "future", "wrongIssuer", "wrongRole", "wrongSubject", "missingExpiry", "missingIssuedAt", "missingType"})
	void signedTokenWithInvalidClaimsIsRejected(String scenario) throws Exception {
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("hirelink").subject("1")
				.claim("role", "STUDENT").claim("token_type", "access")
				.issuedAt(now.minusSeconds(7200)).notBefore(now.minusSeconds(7200));
		if (!scenario.equals("missingExpiry")) {
			claims.expiresAt(scenario.equals("expired") ? now.minusSeconds(1) : now.plusSeconds(3600));
		}
		switch (scenario) {
			case "future" -> claims.notBefore(now.plusSeconds(300));
			case "wrongIssuer" -> claims.issuer("other-service");
			case "wrongRole" -> claims.claim("role", "ADMIN");
			case "wrongSubject" -> claims.subject("not-a-user-id");
			case "missingIssuedAt" -> claims.claims(values -> values.remove("iat"));
			case "missingType" -> claims.claims(values -> values.remove("token_type"));
			default -> { }
		}
		String token = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
		assertError(authenticatedRequest(token, "GET"), 401, "UNAUTHORIZED");
	}

	@Test
	void tokenSaveFailureRollsBackSignupAndDoesNotReturnCookie() throws Exception {
		doThrow(new IllegalStateException("token storage unavailable"))
				.when(refreshTokenRepository).saveAndFlush(any(RefreshToken.class));
		HttpResponse<String> response = signup("rollback@example.com", "password123", "STUDENT");
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertAccountCounts(0, 0);
		assertThat(refreshTokenCount()).isZero();
	}

	@Test
	void tokenSaveFailureDuringLoginKeepsExistingSessions() throws Exception {
		signup("member@example.com", "password123", "STUDENT");
		doThrow(new IllegalStateException("token storage unavailable"))
				.when(refreshTokenRepository).saveAndFlush(any(RefreshToken.class));
		HttpResponse<String> response = login("member@example.com", "password123");
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(refreshTokenCount()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void refreshUsesCurrentRoleAndPreservesExistingLoginRecords(String initialRole) throws Exception {
		HttpResponse<String> first = signup("member@example.com", "password123", initialRole);
		HttpResponse<String> second = login("member@example.com", "password123");
		List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM refresh_token ORDER BY id");
		String currentRole = initialRole.equals("STUDENT") ? "EMPLOYER" : "STUDENT";
		jdbcTemplate.update("UPDATE user_account SET role = ? WHERE email = ?", currentRole, "member@example.com");

		for (HttpResponse<String> loginResponse : List.of(first, second)) {
			HttpResponse<String> refreshed = refresh(refreshCookieValue(loginResponse));
			assertThat(refreshed.statusCode()).isEqualTo(200);
			assertThat(refreshed.headers().firstValue("Cache-Control")).contains("no-store");
			assertThat(refreshed.headers().allValues("Set-Cookie")).isEmpty();
			JsonNode body = objectMapper.readTree(refreshed.body());
			assertThat(body.size()).isEqualTo(3);
			assertThat(body.path("tokenType").asString()).isEqualTo("Bearer");
			assertThat(body.path("expiresIn").asLong()).isEqualTo(3600);
			String accessToken = body.path("accessToken").asString();
			assertThat(accessToken)
					.isNotEqualTo(objectMapper.readTree(loginResponse.body()).path("accessToken").asString());
			JsonNode claims = tokenClaims(accessToken);
			assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(3600);
			HttpResponse<String> authenticated = authenticatedRequest(accessToken, "GET");
			assertThat(authenticated.statusCode()).isEqualTo(200);
			JsonNode currentUser = objectMapper.readTree(authenticated.body());
			assertThat(currentUser.path("role").asString()).isEqualTo(currentRole);
			assertThat(currentUser.path("userId").asLong())
					.isEqualTo(objectMapper.readTree(loginResponse.body()).path("userId").asLong());
		}
		assertThat(jdbcTemplate.queryForList("SELECT * FROM refresh_token ORDER BY id")).isEqualTo(existing);
	}

	@ParameterizedTest
	@ValueSource(strings = {"deleted", "expired", "differentUser"})
	void refreshRejectsInvalidDatabaseRecords(String scenario) throws Exception {
		String token = refreshCookieValue(signup("member@example.com", "password123", "STUDENT"));
		switch (scenario) {
			case "deleted" -> jdbcTemplate.update("DELETE FROM refresh_token");
			case "expired" -> jdbcTemplate.update("UPDATE refresh_token SET expires_at = now() - interval '1 second'");
			case "differentUser" -> {
				jdbcTemplate.update("INSERT INTO user_account (email, role) VALUES (?, ?)", "other@example.com", "STUDENT");
				jdbcTemplate.update("UPDATE refresh_token SET user_id = (SELECT id FROM user_account WHERE email = ?)",
						"other@example.com");
			}
			default -> throw new IllegalArgumentException(scenario);
		}
		assertError(refresh(token), 401, "INVALID_REFRESH_TOKEN");
	}

	@Test
	void withdrawnAccountCannotRefreshTokens() throws Exception {
		String token = refreshCookieValue(signup("withdrawn@example.com", "password123", "STUDENT"));
		jdbcTemplate.update("UPDATE user_account SET withdrawn_at = now() WHERE email = ?", "withdrawn@example.com");
		assertError(refresh(token), 403, "WITHDRAWN_ACCOUNT");
		assertThat(refreshTokenCount()).isEqualTo(1);
	}

	@Test
	void csrfEndpointProvidesHeaderTokenAndCookieWithoutCreatingSession() throws Exception {
		HttpResponse<String> response = csrf();
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = objectMapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(2);
		assertThat(body.path("headerName").asString()).isEqualTo("X-XSRF-TOKEN");
		assertThat(body.path("token").asString()).isNotBlank();
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(response.headers().allValues("Set-Cookie")).hasSize(1);
		assertThat(response.headers().firstValue("Set-Cookie").orElseThrow())
				.contains("XSRF-TOKEN=", "HttpOnly", "Secure", "SameSite=Lax", "Path=/api/auth")
				.doesNotContain("JSESSIONID", "refreshToken");
		assertThat(refreshTokenCount()).isZero();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"not-a-token"})
	void missingOrMalformedRefreshTokenReturns401(String token) throws Exception {
		assertError(refresh(token), 401, "INVALID_REFRESH_TOKEN");
	}

	@ParameterizedTest
	@ValueSource(strings = {"expired", "future", "wrongIssuer", "wrongRole", "wrongSubject",
			"missingExpiry", "missingIssuedAt", "missingType", "accessToken"})
	void refreshRejectsInvalidJwtEvenWhenItsHashIsStored(String scenario) throws Exception {
		signup("member@example.com", "password123", "STUDENT");
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("hirelink").subject("1")
				.claim("role", "STUDENT").claim("token_type", "refresh")
				.issuedAt(now.minusSeconds(7200)).notBefore(now.minusSeconds(7200));
		if (!scenario.equals("missingExpiry")) {
			claims.expiresAt(scenario.equals("expired") ? now.minusSeconds(1) : now.plusSeconds(3600));
		}
		switch (scenario) {
			case "future" -> claims.notBefore(now.plusSeconds(300));
			case "wrongIssuer" -> claims.issuer("other-service");
			case "wrongRole" -> claims.claim("role", "ADMIN");
			case "wrongSubject" -> claims.subject("not-a-user-id");
			case "missingIssuedAt" -> claims.claims(values -> values.remove("iat"));
			case "missingType" -> claims.claims(values -> values.remove("token_type"));
			case "accessToken" -> claims.claim("token_type", "access");
			default -> { }
		}
		String token = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
		jdbcTemplate.update("UPDATE refresh_token SET token_hash = ?", tokenHash(token));
		assertError(refresh(token), 401, "INVALID_REFRESH_TOKEN");
	}

	@Test
	void refreshRejectsTamperedSignatureEvenWhenItsHashIsStored() throws Exception {
		String token = refreshCookieValue(signup("member@example.com", "password123", "STUDENT"));
		int signatureStart = token.lastIndexOf('.') + 1;
		char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
		String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
		jdbcTemplate.update("UPDATE refresh_token SET token_hash = ?", tokenHash(tampered));
		assertError(refresh(tampered), 401, "INVALID_REFRESH_TOKEN");
	}

	@ParameterizedTest
	@ValueSource(strings = {"missingCookie", "missingHeader", "wrongHeader", "otherBrowser", "bearerWithoutCsrf"})
	void cookieAuthenticatedRequestsRequireMatchingCsrfCookieAndHeader(String scenario) throws Exception {
		HttpResponse<String> signedUp = signup("member@example.com", "password123", "STUDENT");
		HttpResponse<String> csrfResponse = csrf();
		String cookies = "refreshToken=" + refreshCookieValue(signedUp);
		if (!scenario.equals("missingCookie") && !scenario.equals("bearerWithoutCsrf")) {
			cookies += "; XSRF-TOKEN=" + cookieValue(csrfResponse, "XSRF-TOKEN");
		}
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/refresh"))
				.timeout(Duration.ofSeconds(20)).header("Cookie", cookies).POST(HttpRequest.BodyPublishers.noBody());
		if (!scenario.equals("missingHeader") && !scenario.equals("bearerWithoutCsrf")) {
			String header = switch (scenario) {
				case "wrongHeader" -> "invalid-csrf-token";
				case "otherBrowser" -> objectMapper.readTree(csrf().body()).path("token").asString();
				default -> objectMapper.readTree(csrfResponse.body()).path("token").asString();
			};
			request.header("X-XSRF-TOKEN", header);
		}
		if (scenario.equals("bearerWithoutCsrf")) {
			request.header("Authorization", "Bearer " + objectMapper.readTree(signedUp.body()).path("accessToken").asString());
		}
		for (String path : List.of("/api/auth/refresh", "/api/auth/logout")) {
			HttpRequest protectedRequest = request.uri(URI.create("http://localhost:" + port + path)).build();
			assertError(httpClient.send(protectedRequest, HttpResponse.BodyHandlers.ofString()), 403, "INVALID_CSRF_TOKEN");
		}
		assertThat(refreshTokenCount()).isEqualTo(1);
	}

	@Test
	void expiredAccessHeaderDoesNotPreventCookieAuthenticatedRequests() throws Exception {
		String refreshToken = refreshCookieValue(signup("member@example.com", "password123", "STUDENT"));
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject("1")
				.claim("role", "STUDENT").claim("token_type", "access")
				.issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(1)).build();
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		assertError(authenticatedRequest(expired, "GET"), 401, "UNAUTHORIZED");
		HttpRequest csrfRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf"))
				.timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + expired).GET().build();
		HttpResponse<String> csrfResponse = httpClient.send(csrfRequest, HttpResponse.BodyHandlers.ofString());
		assertThat(csrfResponse.statusCode()).isEqualTo(200);
		HttpRequest request = refreshRequest(refreshToken, csrfResponse).header("Authorization", "Bearer " + expired).build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		String newToken = objectMapper.readTree(response.body()).path("accessToken").asString();
		assertThat(authenticatedRequest(newToken, "GET").statusCode()).isEqualTo(200);
		HttpRequest logoutRequest = cookieRequest("/api/auth/logout", refreshToken, csrfResponse)
				.header("Authorization", "Bearer " + expired).build();
		assertLogoutSuccess(httpClient.send(logoutRequest, HttpResponse.BodyHandlers.ofString()));
	}

	@Test
	void refreshRequiresCookieInsteadOfRequestBodyOrBearerHeader() throws Exception {
		HttpResponse<String> signedUp = signup("member@example.com", "password123", "STUDENT");
		HttpRequest request = refreshRequest(null, csrf())
				.header("Authorization", "Bearer " + objectMapper.readTree(signedUp.body()).path("accessToken").asString())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
						Map.of("refreshToken", refreshCookieValue(signedUp)))))
				.build();
		assertError(httpClient.send(request, HttpResponse.BodyHandlers.ofString()), 401, "INVALID_REFRESH_TOKEN");
	}

	@Test
	void refreshDatabaseFailureReturnsSafe500WithoutChangingLoginRecords() throws Exception {
		String token = refreshCookieValue(signup("member@example.com", "password123", "STUDENT"));
		List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM refresh_token");
		doThrow(new IllegalStateException("token storage unavailable"))
				.when(refreshTokenRepository).findByTokenHash(anyString());
		HttpResponse<String> response = refresh(token);
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(response.body()).doesNotContain("token storage unavailable", token, "Exception");
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(jdbcTemplate.queryForList("SELECT * FROM refresh_token")).isEqualTo(existing);
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void logoutRevokesOnlyCurrentLoginAndExpiresCookie(String role) throws Exception {
		HttpResponse<String> first = signup("member@example.com", "password123", role);
		HttpResponse<String> second = login("member@example.com", "password123");
		String firstToken = refreshCookieValue(first);
		String secondToken = refreshCookieValue(second);
		Map<String, Object> otherLogin = jdbcTemplate.queryForMap(
				"SELECT * FROM refresh_token WHERE token_hash = ?", tokenHash(secondToken));

		assertLogoutSuccess(logout(firstToken));
		assertThat(refreshTokenCount()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForMap("SELECT * FROM refresh_token")).isEqualTo(otherLogin);
		assertError(refresh(firstToken), 401, "INVALID_REFRESH_TOKEN");
		assertThat(refresh(secondToken).statusCode()).isEqualTo(200);
		assertLogoutSuccess(logout(firstToken));
		assertThat(refreshTokenCount()).isEqualTo(1);
		String existingAccessToken = objectMapper.readTree(first.body()).path("accessToken").asString();
		assertThat(authenticatedRequest(existingAccessToken, "GET").statusCode()).isEqualTo(200);
		assertAccountCounts(1, 1);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"not-a-token"})
	void logoutWithoutStoredTokenStillSucceeds(String token) throws Exception {
		signup("member@example.com", "password123", "STUDENT");
		Map<String, Object> existing = jdbcTemplate.queryForMap("SELECT * FROM refresh_token");
		assertLogoutSuccess(logout(token));
		assertThat(jdbcTemplate.queryForMap("SELECT * FROM refresh_token")).isEqualTo(existing);
	}

	@Test
	void logoutRemovesExpiredRefreshToken() throws Exception {
		signup("member@example.com", "password123", "STUDENT");
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject("1")
				.claim("role", "STUDENT").claim("token_type", "refresh")
				.issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(1)).build();
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		jdbcTemplate.update("UPDATE refresh_token SET token_hash = ?, expires_at = now() - interval '1 second'",
				tokenHash(expired));
		assertLogoutSuccess(logout(expired));
		assertThat(refreshTokenCount()).isZero();
	}

	@Test
	void simultaneousLogoutRequestsBothSucceed() throws Exception {
		String token = refreshCookieValue(signup("member@example.com", "password123", "STUDENT"));
		HttpRequest request = cookieRequest("/api/auth/logout", token, csrf()).build();
		var first = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		var second = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		assertLogoutSuccess(first.get(20, TimeUnit.SECONDS));
		assertLogoutSuccess(second.get(20, TimeUnit.SECONDS));
		assertThat(refreshTokenCount()).isZero();
	}

	@Test
	void logoutDatabaseFailureKeepsCookieAndLoginRecord() throws Exception {
		String token = refreshCookieValue(signup("member@example.com", "password123", "STUDENT"));
		Map<String, Object> existing = jdbcTemplate.queryForMap("SELECT * FROM refresh_token");
		doThrow(new IllegalStateException("token storage unavailable"))
				.when(refreshTokenRepository).deleteByTokenHash(anyString());
		HttpResponse<String> response = logout(token);
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(response.body()).doesNotContain("token storage unavailable", token, "Exception");
		assertThat(jdbcTemplate.queryForMap("SELECT * FROM refresh_token")).isEqualTo(existing);
	}

	private void assertLogoutSuccess(HttpResponse<String> response) throws Exception {
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = objectMapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(1);
		assertThat(body.path("message").asString()).isEqualTo("로그아웃되었습니다.");
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(response.headers().allValues("Set-Cookie")).hasSize(1);
		String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
		assertThat(cookie).contains("refreshToken=", "Max-Age=0", "Path=/api/auth", "HttpOnly", "Secure", "SameSite=Lax");
		assertThat(cookieValue(response, "refreshToken")).isEmpty();
	}

	private void assertTokenStorage(HttpResponse<String> response) throws Exception {
		String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
		assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/auth", "Max-Age=1209600");
		String refreshToken = refreshCookieValue(response);
		String hash = tokenHash(refreshToken);
		Map<String, Object> stored = jdbcTemplate.queryForMap("SELECT * FROM refresh_token WHERE token_hash = ?", hash);
		JsonNode accessClaims = tokenClaims(objectMapper.readTree(response.body()).path("accessToken").asString());
		JsonNode refreshClaims = tokenClaims(refreshToken);
		assertThat(accessClaims.path("token_type").asString()).isEqualTo("access");
		assertThat(refreshClaims.path("token_type").asString()).isEqualTo("refresh");
		assertThat(accessClaims.has("email")).isFalse();
		assertThat(refreshClaims.has("email")).isFalse();
		assertThat(accessClaims.path("exp").asLong() - accessClaims.path("iat").asLong()).isEqualTo(3600);
		assertThat(refreshClaims.path("exp").asLong() - refreshClaims.path("iat").asLong()).isEqualTo(1209600);
		assertThat(((java.sql.Timestamp) stored.get("expires_at")).toInstant().getEpochSecond())
				.isEqualTo(refreshClaims.path("exp").asLong());
		assertThat(((java.sql.Timestamp) stored.get("created_at")).toInstant().getEpochSecond())
				.isEqualTo(refreshClaims.path("iat").asLong());
		assertThat(stored.get("user_id").toString()).isEqualTo(refreshClaims.path("sub").asString());
	}

	private JsonNode tokenClaims(String token) {
		return objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
	}

	private String refreshCookieValue(HttpResponse<String> response) {
		return cookieValue(response, "refreshToken");
	}

	private String cookieValue(HttpResponse<String> response, String name) {
		return response.headers().allValues("Set-Cookie").stream()
				.flatMap(header -> HttpCookie.parse(header).stream())
				.filter(cookie -> cookie.getName().equals(name))
				.findFirst().orElseThrow().getValue();
	}

	private String tokenHash(String token) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
	}

	private HttpResponse<String> csrf() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf"))
				.timeout(Duration.ofSeconds(10)).GET().build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder refreshRequest(String refreshToken, HttpResponse<String> csrfResponse) throws Exception {
		return cookieRequest("/api/auth/refresh", refreshToken, csrfResponse);
	}

	private HttpRequest.Builder cookieRequest(String path, String refreshToken, HttpResponse<String> csrfResponse) throws Exception {
		String cookies = "XSRF-TOKEN=" + cookieValue(csrfResponse, "XSRF-TOKEN");
		if (refreshToken != null) {
			cookies += "; refreshToken=" + refreshToken;
		}
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(20)).header("Cookie", cookies)
				.header("X-XSRF-TOKEN", objectMapper.readTree(csrfResponse.body()).path("token").asString())
				.POST(HttpRequest.BodyPublishers.noBody());
	}

	private HttpResponse<String> refresh(String token) throws Exception {
		return httpClient.send(refreshRequest(token, csrf()).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> logout(String token) throws Exception {
		return httpClient.send(cookieRequest("/api/auth/logout", token, csrf()).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> login(String email, String password) throws Exception {
		return postTo("/api/auth/login", objectMapper.writeValueAsString(Map.of("email", email, "password", password)));
	}

	private HttpResponse<String> postTo(String path, String json) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> authenticatedRequest(String token, String method) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/test/auth/me"))
				.timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
				.method(method, HttpRequest.BodyPublishers.noBody()).build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private int refreshTokenCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token", Integer.class);
	}

	private static String createTestSigningKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

	@RestController
	static class TestAuthController {
		@GetMapping("/test/auth/me")
		Map<String, Object> currentUser(@AuthenticationPrincipal Jwt jwt) {
			return Map.of("userId", Long.parseLong(jwt.getSubject()), "role", jwt.getClaimAsString("role"));
		}

		@PostMapping("/test/auth/me")
		Map<String, Object> authenticatedPost(@AuthenticationPrincipal Jwt jwt) {
			return currentUser(jwt);
		}
	}

	private HttpResponse<String> signup(String email, String password, String role) throws Exception {
		return post(signupJson(email, password, role));
	}

	private String signupJson(String email, String password, String role) {
		return objectMapper.writeValueAsString(Map.of("email", email, "password", password, "role", role));
	}

	private HttpResponse<String> post(String json) throws Exception {
		return httpClient.send(signupRequest(json), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest signupRequest(String json) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/signup"))
				.timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
		JsonNode body = objectMapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(2);
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.path("message").asString()).isNotBlank();
	}

	private void assertAccountCounts(int users, int authAccounts) {
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isEqualTo(users);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_account", Integer.class)).isEqualTo(authAccounts);
	}
}
