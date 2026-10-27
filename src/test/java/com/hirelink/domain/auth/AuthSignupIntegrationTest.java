package com.hirelink.domain.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.member.repository.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSignupIntegrationTest {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoSpyBean
	private UserAccountRepository userAccountRepository;

	@MockitoSpyBean
	private AuthAccountRepository authAccountRepository;

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		// DynamicPropertySource가 연결한 일회용 PostgreSQL 컨테이너만 초기화한다.
		jdbcTemplate.execute("TRUNCATE TABLE auth_account, user_account RESTART IDENTITY");
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void signupStoresBothAccountsAndReturnsOnlyPublicFields(String role) throws Exception {
		HttpResponse<String> response = signup("  Member@Example.COM  ", "password123", role);
		assertThat(response.statusCode()).isEqualTo(201);
		JsonNode body = objectMapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(3);
		assertThat(body.path("userId").asLong()).isPositive();
		assertThat(body.path("email").asString()).isEqualTo("member@example.com");
		assertThat(body.path("role").asString()).isEqualTo(role);
		assertThat(response.headers().firstValue("Authorization")).isEmpty();

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
	void unrelatedPathDoesNotRequireAuthenticationOrRedirectToLogin() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/not-existing"))
				.timeout(Duration.ofSeconds(10)).GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
		assertThat(response.headers().firstValue("Location")).isEmpty();
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
