package com.hirelink.domain.auth;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.support.PostgreSQLIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		"auth.local.enabled=false",
		"auth.qa-login.allowed-emails= QA.STUDENT@example.com , qa.employer@example.com, ,"
				+ "missing@example.com,social@example.com,withdrawn@example.com,*.example.net,@example.org"
})
class QaLoginIntegrationTest extends PostgreSQLIntegrationTest {

	@DynamicPropertySource
	static void securityProperties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
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
	private PasswordEncoder passwords;
	@Autowired
	private ObjectMapper json;

	private final HttpClient client = HttpClient.newHttpClient();

	@BeforeEach
	void clearDatabase() {
		jdbc.execute("TRUNCATE TABLE user_account, social_auth_session RESTART IDENTITY CASCADE");
	}

	@ParameterizedTest
	@EnumSource(MemberRole.class)
	void allowedAccountsKeepIdentityAndUseNormalSessionRefreshAndLogout(MemberRole role) throws Exception {
		String email = "qa." + role.name().toLowerCase(Locale.ROOT) + "@example.com";
		UserAccount member = createLocalAccount(email, role);
		String passwordHash = accounts.findByProviderAndProviderUserId(AuthProvider.LOCAL, email)
				.orElseThrow().getPasswordHash();

		HttpResponse<String> first = login("  " + email.toUpperCase(Locale.ROOT) + "  ", "password123");
		assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
		JsonNode body = json.readTree(first.body());
		assertThat(body.path("userId").asLong()).isEqualTo(member.getId());
		assertThat(body.path("role").asString()).isEqualTo(role.name());
		assertThat(body.path("tokenType").asString()).isEqualTo("Bearer");
		assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(first.headers().allValues("Set-Cookie"))
				.anySatisfy(value -> assertThat(value).contains("refreshToken=", "HttpOnly", "Path=/api/auth"));
		String firstAccess = body.path("accessToken").asString();
		String firstRefresh = cookieValue(first, "refreshToken");
		HttpResponse<String> currentUser = get("/api/users/me/onboarding", firstAccess);
		assertThat(currentUser.statusCode()).as(currentUser.body()).isEqualTo(200);
		assertThat(json.readTree(currentUser.body()).path("userId").asLong()).isEqualTo(member.getId());
		assertThat(json.readTree(currentUser.body()).path("role").asString()).isEqualTo(role.name());

		HttpResponse<String> second = login(email, "password123");
		assertThat(second.statusCode()).as(second.body()).isEqualTo(200);
		String secondAccess = json.readTree(second.body()).path("accessToken").asString();
		String secondRefresh = cookieValue(second, "refreshToken");
		HttpResponse<String> refreshed = cookiePost("/api/auth/refresh", firstRefresh);
		assertThat(refreshed.statusCode()).as(refreshed.body()).isEqualTo(200);
		String renewedAccess = json.readTree(refreshed.body()).path("accessToken").asString();
		assertThat(get("/api/users/me/onboarding", renewedAccess).statusCode()).isEqualTo(200);

		assertThat(cookiePost("/api/auth/logout", firstRefresh).statusCode()).isEqualTo(200);
		assertError(get("/api/users/me/onboarding", firstAccess), 401, "UNAUTHORIZED");
		assertError(get("/api/users/me/onboarding", renewedAccess), 401, "UNAUTHORIZED");
		assertError(cookiePost("/api/auth/refresh", firstRefresh), 401, "INVALID_REFRESH_TOKEN");
		assertThat(get("/api/users/me/onboarding", secondAccess).statusCode()).isEqualTo(200);
		assertThat(cookiePost("/api/auth/refresh", secondRefresh).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class)).isEqualTo(1L);
		assertThat(users.count()).isEqualTo(1);
		assertThat(accounts.count()).isEqualTo(1);
		assertThat(accounts.findByProviderAndProviderUserId(AuthProvider.LOCAL, email)
				.orElseThrow().getPasswordHash()).isEqualTo(passwordHash);
	}

	@ParameterizedTest
	@ValueSource(strings = {"member@example.com", "other@sub.example.net", "member@example.org"})
	void unlistedAccountsAndWildcardMatchesRemainForbidden(String email) throws Exception {
		createLocalAccount(email, MemberRole.STUDENT);
		assertError(login(email, "password123"), 403, "FORBIDDEN");
		assertNoRefreshToken();
	}

	@Test
	void allowedEmailsStillRequireExistingLocalCredentialsAndCorrectPassword() throws Exception {
		createLocalAccount("qa.student@example.com", MemberRole.STUDENT);
		UserAccount social = users.saveAndFlush(new UserAccount("social@example.com", MemberRole.STUDENT));
		accounts.saveAndFlush(new AuthAccount(social, AuthProvider.GOOGLE, "google-user-id", null));
		assertError(login("qa.student@example.com", "wrong-password123"), 401, "INVALID_CREDENTIALS");
		assertError(login("missing@example.com", "password123"), 401, "INVALID_CREDENTIALS");
		assertError(login("social@example.com", "password123"), 401, "INVALID_CREDENTIALS");
		assertThat(users.count()).isEqualTo(2);
		assertThat(accounts.count()).isEqualTo(2);
		assertNoRefreshToken();
	}

	@Test
	void withdrawnAllowedAccountCannotLogInOrRestoreAccount() throws Exception {
		createLocalAccount("withdrawn@example.com", MemberRole.STUDENT);
		jdbc.update("UPDATE user_account SET withdrawn_at = now() WHERE email = ?", "withdrawn@example.com");
		assertError(login("withdrawn@example.com", "password123"), 403, "WITHDRAWN_ACCOUNT");
		assertError(login("withdrawn@example.com", "wrong-password123"), 401, "INVALID_CREDENTIALS");
		assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NOT NULL FROM user_account WHERE email = ?",
				Boolean.class, "withdrawn@example.com")).isTrue();
		assertNoRefreshToken();
	}

	@ParameterizedTest
	@EnumSource(MemberRole.class)
	void allowlistDoesNotEnableSignup(MemberRole role) throws Exception {
		String email = "qa." + role.name().toLowerCase(Locale.ROOT) + "@example.com";
		assertError(post("/api/auth/signup", Map.of("email", email, "password", "password123", "role", role.name())),
				403, "FORBIDDEN");
		assertThat(users.count()).isZero();
		assertThat(accounts.count()).isZero();
		assertNoRefreshToken();
	}

	private UserAccount createLocalAccount(String email, MemberRole role) {
		UserAccount user = users.saveAndFlush(new UserAccount(email, role));
		accounts.saveAndFlush(new AuthAccount(user, AuthProvider.LOCAL, email, passwords.encode("password123")));
		return user;
	}

	private void assertNoRefreshToken() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class)).isZero();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo(code);
		assertThat(json.readTree(response.body()).has("accessToken")).isFalse();
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
	}

	private HttpResponse<String> login(String email, String password) throws Exception {
		return post("/api/auth/login", Map.of("email", email, "password", password));
	}

	private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
		return client.send(request(path).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path, String accessToken) throws Exception {
		HttpRequest.Builder request = request(path);
		if (accessToken != null) { request.header("Authorization", "Bearer " + accessToken); }
		return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> cookiePost(String path, String refreshToken) throws Exception {
		HttpResponse<String> csrf = get("/api/auth/csrf", null);
		assertThat(csrf.statusCode()).as(csrf.body()).isEqualTo(200);
		return client.send(request(path)
				.header("Cookie", "refreshToken=" + refreshToken + "; XSRF-TOKEN=" + cookieValue(csrf, "XSRF-TOKEN"))
				.header("X-XSRF-TOKEN", json.readTree(csrf.body()).path("token").asString())
				.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
	}

	private String cookieValue(HttpResponse<String> response, String name) {
		return response.headers().allValues("Set-Cookie").stream()
				.flatMap(header -> HttpCookie.parse(header).stream())
				.filter(cookie -> cookie.getName().equals(name)).map(HttpCookie::getValue).findFirst().orElseThrow();
	}
}
