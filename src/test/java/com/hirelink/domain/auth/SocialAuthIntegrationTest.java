package com.hirelink.domain.auth;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.service.SocialAuthService;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.support.PostgreSQLIntegrationTest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"auth.local.enabled=false", "auth.withdrawal.enabled=true", "auth.withdrawal.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "auth.withdrawal.cleanup-initial-delay=3600000"})
class SocialAuthIntegrationTest extends PostgreSQLIntegrationTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final RSAKey SIGNING_KEY = signingKey();
	private static final Map<String, Grant> GRANTS = new ConcurrentHashMap<>();
	private static final HttpServer PROVIDER = providerServer();
	private static final String PROVIDER_URL = "http://localhost:" + PROVIDER.getAddress().getPort();
	private static final String FRONTEND = "http://localhost/social/callback";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
		registry.add("auth.cookie.secure", () -> false);
		registry.add("server.servlet.session.cookie.secure", () -> false);
		registry.add("auth.social.enabled", () -> true);
		registry.add("auth.social.callback-base-url", () -> "http://localhost");
		registry.add("auth.social.frontend-redirect-uri", () -> FRONTEND);
		for (String provider : new String[]{"google", "kakao"}) {
			String prefix = "auth.social." + provider + ".";
			registry.add(prefix + "client-id", () -> provider + "-test-client");
			registry.add(prefix + "client-secret", () -> "test-client-secret");
			registry.add(prefix + "authorization-uri", () -> PROVIDER_URL + "/authorize");
			registry.add(prefix + "token-uri", () -> PROVIDER_URL + "/token");
			registry.add(prefix + "jwk-set-uri", () -> PROVIDER_URL + "/jwks");
			registry.add(prefix + "issuer-uri", () -> PROVIDER_URL);
		}
	}

	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private UserAccountRepository users;
	@Autowired
	private AuthAccountRepository accounts;
	@Autowired
	private SocialAuthService service;
	@Autowired
	private JwtDecoder jwtDecoder;

	private CookieManager cookies;
	private HttpClient browser;

	@BeforeEach
	void prepare() {
		jdbc.execute("TRUNCATE TABLE user_account, social_auth_session RESTART IDENTITY CASCADE");
		GRANTS.clear();
		cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		browser = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
	}

	@AfterAll
	static void stopProvider() {
		PROVIDER.stop(0);
	}

	@ParameterizedTest
	@ValueSource(strings = {"google", "kakao"})
	void newSocialIdentityCreatesOnlyThirtyMinuteSignupProgress(String provider) throws Exception {
		Instant before = Instant.now();
		HttpResponse<String> callback = authenticate(provider, "STUDENT", "new-subject", null, "valid");
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(callback.headers().firstValue("Location")).contains(FRONTEND);
		assertThat(callback.headers().firstValue("Cache-Control")).contains("no-store");
		String cookieHeader = callback.headers().allValues("Set-Cookie").stream()
				.filter(value -> value.startsWith("socialSignup=")).findFirst().orElseThrow();
		assertThat(cookieHeader).contains("HttpOnly", "SameSite=Lax", "Path=/api/auth", "Max-Age=1800");
		String token = cookie("socialSignup");
		Map<String, Object> session = jdbc.queryForMap("SELECT * FROM social_auth_session");
		assertThat(session.get("token_hash")).isEqualTo(hash(token)).isNotEqualTo(token);
		assertThat(session.get("provider_user_id")).isEqualTo("new-subject");
		assertThat(session.get("provider")).isEqualTo(provider.toUpperCase(java.util.Locale.ROOT));

		HttpResponse<String> result = result();
		assertThat(result.statusCode()).isEqualTo(200);
		JsonNode body = JSON.readTree(result.body());
		assertThat(body.path("status").asString()).isEqualTo("SIGNUP_REQUIRED");
		assertThat(body.path("role").asString()).isEqualTo("STUDENT");
		assertThat(body.path("email").isNull()).isTrue();
		assertThat(body.path("login").isNull()).isTrue();
		assertThat(Instant.parse(body.path("signupExpiresAt").asString()))
				.isBetween(before.plusSeconds(1800), Instant.now().plusSeconds(1800));
		assertThat(JSON.readTree(result().body()).path("signupExpiresAt")).isEqualTo(body.path("signupExpiresAt"));
		assertThat(users.count()).isZero();
		assertThat(accounts.count()).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class)).isZero();
		assertThat(get("/api/students/me/profile").statusCode()).isEqualTo(401);
		assertThat(result.headers().allValues("Set-Cookie")).noneMatch(value -> value.startsWith("refreshToken="));
	}

	@ParameterizedTest
	@ValueSource(strings = {"google", "kakao"})
	void existingIdentityUsesMemberIdAndExchangesResultOnlyOnce(String provider) throws Exception {
		UserAccount member = socialMember(provider, "existing", null, MemberRole.EMPLOYER);
		authenticate(provider, "EMPLOYER", "existing", "provider@example.com", "valid");
		String originalCookie = cookie("socialSignup");
		HttpResponse<String> response = result();
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = JSON.readTree(response.body());
		assertThat(body.path("status").asString()).isEqualTo("LOGIN");
		assertThat(body.path("email").isNull()).isTrue();
		assertThat(body.path("signupExpiresAt").isNull()).isTrue();
		JsonNode login = body.path("login");
		assertThat(login.path("userId").asLong()).isEqualTo(member.getId());
		assertThat(login.path("expiresIn").asLong()).isEqualTo(3600);
		assertThat(jwtDecoder.decode(login.path("accessToken").asString()).getSubject()).isEqualTo(member.getId().toString());
		assertThat(response.headers().allValues("Set-Cookie")).anyMatch(value ->
				value.startsWith("refreshToken=") && value.contains("HttpOnly") && value.contains("Max-Age=1209600"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM social_auth_session", Long.class)).isZero();
		setSignupCookie(originalCookie);
		assertError(result(), 401, "INVALID_SOCIAL_SESSION");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class)).isEqualTo(1);
	}

	@Test
	void matchingEmailDoesNotLinkDifferentSocialIdentities() throws Exception {
		socialMember("google", "google-member", "shared@example.com", MemberRole.STUDENT);
		authenticate("kakao", "STUDENT", "kakao-new", "shared@example.com", "valid");
		JsonNode body = JSON.readTree(result().body());
		assertThat(body.path("status").asString()).isEqualTo("SIGNUP_REQUIRED");
		assertThat(body.path("email").asString()).isEqualTo("shared@example.com");
		assertThat(users.count()).isEqualTo(1);
		socialMember("kakao", "another-member", "shared@example.com", MemberRole.STUDENT);
		assertThat(users.count()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = {"google", "kakao"})
	void socialLoginRefreshAndLogoutRemainAvailableWithLocalAuthDisabled(String provider) throws Exception {
		socialMember(provider, "member", "member@example.com", MemberRole.STUDENT);
		authenticate(provider, "STUDENT", "member", "member@example.com", "valid");
		var signedIn = result();
        assertThat(signedIn.statusCode()).isEqualTo(200);
        String originalAccess = JSON.readTree(signedIn.body()).path("login").path("accessToken").asString();
        String sessionId = jwtDecoder.decode(originalAccess).getClaimAsString("sid");
        var accountsBefore = jdbc.queryForList("select * from auth_account");
        var userBefore = jdbc.queryForList("select * from user_account");
		assertError(postJson("/api/auth/login",
				"{\"email\":\"member@example.com\",\"password\":\"password123\"}"), 403, "FORBIDDEN");
		HttpResponse<String> refreshed = postWithCsrf("/api/auth/refresh");
		assertThat(refreshed.statusCode()).isEqualTo(200);
		String refreshedAccess = JSON.readTree(refreshed.body()).path("accessToken").asString();
        assertThat(jwtDecoder.decode(refreshedAccess).getClaimAsString("sid")).isEqualTo(sessionId);
		HttpResponse<String> logout = postWithCsrf("/api/auth/logout");
		assertThat(logout.statusCode()).isEqualTo(200);
		assertThat(logout.headers().allValues("Set-Cookie"))
				.anyMatch(value -> value.startsWith("refreshToken=") && value.contains("Max-Age=0"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class)).isZero();
		assertError(postWithCsrf("/api/auth/refresh"), 401, "INVALID_REFRESH_TOKEN");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jwtDecoder.decode(originalAccess)).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jwtDecoder.decode(refreshedAccess)).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        assertThat(jdbc.queryForList("select * from auth_account")).isEqualTo(accountsBefore);
        assertThat(jdbc.queryForList("select * from user_account")).isEqualTo(userBefore);
        authenticate(provider, "STUDENT", "member", "member@example.com", "valid");
        var again = result();
        assertThat(again.statusCode()).isEqualTo(200);
        String newAccess = JSON.readTree(again.body()).path("login").path("accessToken").asString();
        assertThat(jwtDecoder.decode(newAccess).getClaimAsString("sid")).isNotEqualTo(sessionId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jwtDecoder.decode(originalAccess)).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
	}

	@Test
	void existingMemberCannotChangeRoleThroughLogin() throws Exception {
		socialMember("google", "student", "student@example.com", MemberRole.STUDENT);
		HttpResponse<String> response = authenticate("google", "EMPLOYER", "student", null, "valid");
		assertThat(response.headers().firstValue("Location")).contains(FRONTEND + "?error=SOCIAL_ROLE_MISMATCH");
		assertThat(jdbc.queryForObject("SELECT role FROM user_account", String.class)).isEqualTo("STUDENT");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM social_auth_session", Long.class)).isZero();
		assertThat(accounts.count()).isEqualTo(1);
	}

	@Test
	void withdrawnMemberIsRejectedAgainWhenExchangingResult() throws Exception {
		socialMember("google", "withdrawn", null, MemberRole.STUDENT);
		authenticate("google", "STUDENT", "withdrawn", null, "valid");
		jdbc.update("UPDATE user_account SET withdrawn_at = now()");
		assertError(result(), 403, "WITHDRAWN_ACCOUNT");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class)).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"wrong-nonce", "wrong-audience", "wrong-issuer", "expired", "wrong-signature"})
	void invalidIdTokenCannotCreateSignupProgress(String problem) throws Exception {
		HttpResponse<String> response = authenticate("google", "STUDENT", "new", null, problem);
		assertThat(response.headers().firstValue("Location")).contains(FRONTEND + "?error=SOCIAL_AUTH_FAILED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM social_auth_session", Long.class)).isZero();
		assertThat(users.count()).isZero();
	}

	@Test
	void callbackNeedsMatchingStateAndCannotBeReplayed() throws Exception {
		Map<String, String> query = authorization("google", "STUDENT");
		HttpResponse<String> wrongState = get("/api/auth/social/callback/google?code=unissued&state=wrong-state");
		assertThat(wrongState.headers().firstValue("Location")).contains(FRONTEND + "?error=SOCIAL_AUTH_FAILED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM social_auth_session", Long.class)).isZero();
		query = authorization("google", "STUDENT");
		String code = issueCode(query, "member", null, "valid");
		String callback = "/api/auth/social/callback/google?code=" + code + "&state=" + query.get("state");
		assertThat(get(callback).headers().firstValue("Location")).contains(FRONTEND);
		assertThat(get(callback).headers().firstValue("Location")).contains(FRONTEND + "?error=SOCIAL_AUTH_FAILED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM social_auth_session", Long.class)).isEqualTo(1);
	}

	@Test
	void expiredProgressIsRejectedBeforeCleanupAndCleanupKeepsValidProgress() throws Exception {
		setSignupCookie(service.createSession(AuthProvider.GOOGLE, "expired", MemberRole.STUDENT, null));
		jdbc.update("UPDATE social_auth_session SET expires_at = now() - interval '1 second'");
		service.createSession(AuthProvider.KAKAO, "valid", MemberRole.STUDENT, null);
		assertError(result(), 401, "INVALID_SOCIAL_SESSION");
		service.deleteExpiredSessions();
		assertThat(jdbc.queryForList("SELECT provider_user_id FROM social_auth_session", String.class)).containsExactly("valid");
	}

	@Test
	void resultRequiresCsrfAndCookieEvenWithBearerHeader() throws Exception {
		setSignupCookie(service.createSession(AuthProvider.GOOGLE, "new", MemberRole.STUDENT, null));
		HttpRequest request = HttpRequest.newBuilder(uri("/api/auth/social/result"))
				.header("Authorization", "Bearer invalid-token").POST(HttpRequest.BodyPublishers.noBody()).build();
		assertError(browser.send(request, HttpResponse.BodyHandlers.ofString()), 403, "INVALID_CSRF_TOKEN");
		cookies.getCookieStore().removeAll();
		assertError(result(), 401, "INVALID_SOCIAL_SESSION");
	}

	@Test
	void unverifiedEmailIsNotUsed() throws Exception {
		authenticate("google", "STUDENT", "unverified", "unverified@example.com", "unverified-email");
		assertThat(JSON.readTree(result().body()).path("email").isNull()).isTrue();
	}

	@Test
	void startRejectsUnknownProviderAndMemberRole() throws Exception {
		assertError(get("/api/auth/social/other?role=STUDENT"), 400, "INVALID_INPUT");
		assertError(get("/api/auth/social/google?role=ADMIN"), 400, "INVALID_INPUT");
		assertError(get("/api/auth/social/google"), 400, "INVALID_INPUT");
	}

	private UserAccount socialMember(String provider, String subject, String email, MemberRole role) {
		UserAccount user = users.saveAndFlush(new UserAccount(email, role));
		accounts.saveAndFlush(new AuthAccount(user, AuthProvider.valueOf(provider.toUpperCase(java.util.Locale.ROOT)), subject, null));
		return user;
	}

	private Map<String, String> authorization(String provider, String role) throws Exception {
		HttpResponse<String> start = get("/api/auth/social/" + provider + "?role=" + role);
		assertThat(start.statusCode()).isEqualTo(302);
		HttpResponse<String> redirect = get(start.headers().firstValue("Location").orElseThrow());
		assertThat(redirect.statusCode()).isEqualTo(302);
		Map<String, String> query = parameters(URI.create(redirect.headers().firstValue("Location").orElseThrow()).getRawQuery());
		assertThat(query).containsEntry("response_type", "code").containsEntry("code_challenge_method", "S256");
		assertThat(query.get("state")).isNotBlank();
		assertThat(query.get("nonce")).isNotBlank();
		assertThat(query.get("code_challenge")).isNotBlank();
		assertThat(query.get("redirect_uri")).isEqualTo("http://localhost/api/auth/social/callback/" + provider);
		return query;
	}

	private HttpResponse<String> authenticate(String provider, String role, String subject, String email, String problem) throws Exception {
		Map<String, String> query = authorization(provider, role);
		String code = issueCode(query, subject, email, problem);
		return get("/api/auth/social/callback/" + provider + "?code=" + code + "&state=" + query.get("state"));
	}

	private String issueCode(Map<String, String> query, String subject, String email, String problem) {
		String code = UUID.randomUUID().toString();
		GRANTS.put(code, new Grant(query, subject, email, problem));
		return code;
	}

	private HttpResponse<String> result() throws Exception {
		return postWithCsrf("/api/auth/social/result");
	}

	private HttpResponse<String> postWithCsrf(String path) throws Exception {
		JsonNode csrf = JSON.readTree(get("/api/auth/csrf").body());
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("X-XSRF-TOKEN", csrf.path("token").asString()).POST(HttpRequest.BodyPublishers.noBody()).build();
		return browser.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> postJson(String path, String json) throws Exception {
		return browser.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws Exception {
		return browser.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

	private String cookie(String name) {
		return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals(name))
				.findFirst().orElseThrow().getValue();
	}

	private void setSignupCookie(String value) {
		HttpCookie cookie = new HttpCookie("socialSignup", value);
		cookie.setPath("/api/auth");
		cookie.setVersion(0);
		cookies.getCookieStore().add(uri("/"), cookie);
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo(code);
	}


    @Test
    void googleOfflineConsentCapturesEncryptedCredentialButNeverReturnsItToBrowser() throws Exception {
        var member=socialMember("google", "with-refresh", null, MemberRole.STUDENT);
        var query=authorization("google", "STUDENT");
        assertThat(query).containsEntry("access_type","offline").containsEntry("prompt","consent");
        String code=issueCode(query,"with-refresh",null,"valid");
        var callback=get("/api/auth/social/callback/google?code="+code+"&state="+query.get("state"));
        assertThat(callback.headers().firstValue("Location")).contains(FRONTEND);
        String encrypted=jdbc.queryForObject("select revocation_token from auth_account where user_id=?",String.class,member.getId());
        assertThat(encrypted).isNotBlank().doesNotContain("provider-test-refresh-token");
        var protector=new com.hirelink.domain.auth.service.ProviderTokenProtector(true,"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        assertThat(protector.decrypt(encrypted,"GOOGLE:with-refresh")).isEqualTo("provider-test-refresh-token");
        var result=result();
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        assertThat(result.body()).doesNotContain(encrypted,"provider-test-refresh-token","provider-test-access-token");
    }

	private static HttpServer providerServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/jwks", exchange -> respond(exchange, 200, new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
			server.createContext("/token", exchange -> {
				Map<String, String> form = parameters(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				Grant grant = GRANTS.remove(form.get("code"));
				try {
					String challenge = form.get("code_verifier") == null ? "" : Base64.getUrlEncoder().withoutPadding()
							.encodeToString(MessageDigest.getInstance("SHA-256").digest(form.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
					if (grant == null || !challenge.equals(grant.query().get("code_challenge"))
							|| !grant.query().get("redirect_uri").equals(form.get("redirect_uri"))
							|| !"test-client-secret".equals(form.get("client_secret"))) {
						respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
						return;
					}
					Instant now = Instant.now();
					JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(grant.subject())
							.issuer("wrong-issuer".equals(grant.problem()) ? "http://invalid.example" : PROVIDER_URL)
							.audience("wrong-audience".equals(grant.problem()) ? "another-client" : grant.query().get("client_id"))
							.issueTime(Date.from(now.minusSeconds(600)))
							.expirationTime(Date.from("expired".equals(grant.problem()) ? now.minusSeconds(300) : now.plusSeconds(300)))
							.claim("nonce", "wrong-nonce".equals(grant.problem()) ? "another-nonce" : grant.query().get("nonce"));
					if (grant.email() != null) {
						claims.claim("email", grant.email()).claim("email_verified", !"unverified-email".equals(grant.problem()));
					}
					SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(), claims.build());
					jwt.sign(new RSASSASigner("wrong-signature".equals(grant.problem()) ? signingKey() : SIGNING_KEY));
					respond(exchange, 200, JSON.writeValueAsString(Map.of("access_token", "provider-test-access-token",
							"token_type", "Bearer", "expires_in", 300, "id_token", jwt.serialize(), "refresh_token", "provider-test-refresh-token")));
				} catch (Exception exception) {
					respond(exchange, 500, "{\"error\":\"server_error\"}");
				}
			});
			server.start();
			return server;
		} catch (IOException exception) { throw new IllegalStateException(exception); }
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (var output = exchange.getResponseBody()) { output.write(bytes); }
	}

	private static RSAKey signingKey() {
		try { return new RSAKeyGenerator(2048).keyID("test-key").generate(); }
		catch (JOSEException exception) { throw new IllegalStateException(exception); }
	}

	private static Map<String, String> parameters(String value) {
		Map<String, String> parameters = new HashMap<>();
		for (String pair : value.split("&")) {
			String[] parts = pair.split("=", 2);
			parameters.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
					parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
		}
		return parameters;
	}

	private static String hash(String token) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
	}

	private record Grant(Map<String, String> query, String subject, String email, String problem) { }
}
