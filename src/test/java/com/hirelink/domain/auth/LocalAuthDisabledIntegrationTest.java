package com.hirelink.domain.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
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
@TestPropertySource(properties = "auth.local.enabled=false")
class LocalAuthDisabledIntegrationTest extends PostgreSQLIntegrationTest {

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
	private PasswordEncoder passwordEncoder;
	@Autowired
	private ObjectMapper json;

	private final HttpClient client = HttpClient.newHttpClient();

	@BeforeEach
	void clearDatabase() {
		jdbc.execute("TRUNCATE TABLE user_account, social_auth_session RESTART IDENTITY CASCADE");
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void disabledSignupCannotCreateAccountOrToken(String role) throws Exception {
		assertForbidden(post("/api/auth/signup", """
				{"email":"member@example.com","password":"password123","role":"%s"}
				""".formatted(role)));
		assertThat(users.count()).isZero();
		assertThat(accounts.count()).isZero();
		assertNoRefreshToken();
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void disabledLoginRejectsExistingAndMissingAccountsWithoutChangingStoredAccount(String role) throws Exception {
		UserAccount member = users.saveAndFlush(new UserAccount("member@example.com", MemberRole.valueOf(role)));
		String passwordHash = passwordEncoder.encode("password123");
		accounts.saveAndFlush(new AuthAccount(member, AuthProvider.LOCAL, "member@example.com", passwordHash));
		for (String email : new String[]{"member@example.com", "missing@example.com"}) {
			for (String password : new String[]{"password123", "wrong-password123"}) {
				assertForbidden(post("/api/auth/login", """
						{"email":"%s","password":"%s"}
						""".formatted(email, password)));
			}
		}
		assertThat(users.count()).isEqualTo(1);
		assertThat(accounts.count()).isEqualTo(1);
		assertThat(accounts.findByProviderAndProviderUserId(AuthProvider.LOCAL, "member@example.com")
				.orElseThrow().getPasswordHash()).isEqualTo(passwordHash);
		assertNoRefreshToken();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/api/auth/signup", "/api/auth/login"})
	void invalidRequestRetainsValidationResponse(String path) throws Exception {
		HttpResponse<String> response = post(path, "{}");
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo("INVALID_INPUT");
		assertNoRefreshToken();
	}

	private void assertForbidden(HttpResponse<String> response) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
		JsonNode error = json.readTree(response.body());
		assertThat(error.path("code").asString()).isEqualTo("FORBIDDEN");
		assertThat(error.path("message").asString()).isEqualTo("접근 권한이 없습니다.");
		assertThat(error.has("accessToken")).isFalse();
		assertThat(response.headers().allValues("Set-Cookie"))
				.noneMatch(value -> value.startsWith("refreshToken="));
	}

	private void assertNoRefreshToken() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class)).isZero();
	}

	private HttpResponse<String> post(String path, String body) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
}
