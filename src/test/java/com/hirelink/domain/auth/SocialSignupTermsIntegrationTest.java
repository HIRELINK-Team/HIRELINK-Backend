package com.hirelink.domain.auth;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.service.SocialAuthService;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.support.PostgreSQLIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialSignupTermsIntegrationTest extends PostgreSQLIntegrationTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String PATH = "/api/auth/social/terms";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
		registry.add("auth.cookie.secure", () -> false);
		registry.add("auth.social.enabled", () -> true);
		registry.add("auth.social.callback-base-url", () -> "http://localhost");
		registry.add("auth.social.frontend-redirect-uri", () -> "http://localhost/social/callback");
		for (String provider : new String[]{"google", "kakao"}) {
			registry.add("auth.social." + provider + ".client-id", () -> provider + "-test-client");
			registry.add("auth.social." + provider + ".client-secret", () -> "test-client-secret");
		}
	}

	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private SocialAuthService socialAuthService;
	@Autowired
	private UserAccountRepository users;
	@Autowired
	private AuthAccountRepository accounts;
	private CookieManager cookies;
	private HttpClient browser;
	private List<Long> requiredIds;
	private long optionalId;

	@AfterEach
	void resetTermVersions() { jdbc.update("update term_type set current_version='v1'"); }

	@BeforeEach
	void setUp() {
		jdbc.update("update term_type set current_version='v1'");
		jdbc.execute("TRUNCATE TABLE user_account, social_auth_session RESTART IDENTITY CASCADE");
		cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		browser = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
		requiredIds = jdbc.queryForList(
				"select id from term_type where is_social_signup and is_required and not is_student_registration order by id", Long.class);
		optionalId = jdbc.queryForObject(
				"select id from term_type where is_social_signup and not is_required", Long.class);
		setSignupCookie(socialAuthService.createSession(AuthProvider.GOOGLE, "new-student", MemberRole.STUDENT, null));
	}

	@Test
	void returnsOnlySignupTermsWithDefaultConsent() throws Exception {
		HttpResponse<String> response = getTerms();
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode terms = JSON.readTree(response.body()).path("terms");
		assertThat(terms.size()).isEqualTo(4);
		assertThat(terms.get(0).path("termTypeName").asString()).isEqualTo("서비스 이용약관");
		assertThat(requiredIds).hasSize(3);
		for (JsonNode term : terms) {
			assertThat(term.path("isRequired").asBoolean()).isEqualTo(requiredIds.contains(term.path("termTypeId").asLong()));
			assertThat(term.path("isAgreed").asBoolean()).isFalse();
			assertThat(term.path("agreedAt").isNull()).isTrue();
		}
		assertThat(jdbc.queryForObject("select count(*) from social_auth_session_term_agreement", Long.class)).isZero();
	}

	@Test
	void savesTemporaryConsentWithoutCreatingAccountOrExtendingExpiry() throws Exception {
		Object expiresAt = jdbc.queryForObject("select expires_at from social_auth_session", Object.class);
		HttpResponse<String> saved = putTerms(validBody(true), csrf());
		assertThat(saved.statusCode()).isEqualTo(200);
		assertThat(saved.headers().firstValue("Cache-Control")).contains("no-store");
		for (JsonNode term : JSON.readTree(saved.body()).path("terms")) {
			assertThat(term.path("isAgreed").asBoolean()).isTrue();
			assertThat(term.path("agreedAt").isTextual()).isTrue();
		}
		assertThat(JSON.readTree(getTerms().body())).isEqualTo(JSON.readTree(saved.body()));
		assertThat(jdbc.queryForObject("select expires_at from social_auth_session", Object.class)).isEqualTo(expiresAt);
		assertThat(jdbc.queryForObject("select count(*) from user_account", Long.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement", Long.class)).isZero();
	}

	@Test
	void repeatedConsentPreservesTimeAndOmittedOptionalConsentIsCleared() throws Exception {
		String csrf = csrf();
		JsonNode first = JSON.readTree(putTerms(validBody(true), csrf).body());
		assertThat(JSON.readTree(putTerms(validBody(true), csrf).body())).isEqualTo(first);
		HttpResponse<String> updated = putTerms(validBody(false), csrf);
		assertThat(updated.statusCode()).isEqualTo(200);
		for (JsonNode term : JSON.readTree(updated.body()).path("terms")) {
			if (term.path("termTypeId").asLong() == optionalId) {
				assertThat(term.path("isAgreed").asBoolean()).isFalse();
				assertThat(term.path("agreedAt").isNull()).isTrue();
			} else {
				assertThat(term).isEqualTo(first.path("terms").get(requiredIds.indexOf(term.path("termTypeId").asLong())));
			}
		}
	}

	@Test
	void missingOrRejectedRequiredTermDoesNotChangeSavedConsent() throws Exception {
		String csrf = csrf();
		String before = putTerms(validBody(true), csrf).body();
		assertError(putTerms("{\"agreements\":[{\"termTypeId\":" + requiredIds.get(0) + ",\"isAgreed\":true}]}", csrf), 400, "INVALID_INPUT");
		for (String body : List.of(
				"{\"agreements\":[{\"termTypeId\":" + optionalId + ",\"version\":\"v1\",\"isAgreed\":true}]}",
				validBody(true).replace("true", "false"))) {
			assertError(putTerms(body, csrf), 400, "REQUIRED_TERMS_NOT_AGREED");
			assertThat(JSON.readTree(getTerms().body())).isEqualTo(JSON.readTree(before));
		}
	}

	@Test
	void duplicateOrInactiveOrUnknownTermIsRejected() throws Exception {
		String csrf = csrf();
		String agreement = "{\"termTypeId\":" + requiredIds.get(0) + ",\"version\":\"v1\",\"isAgreed\":true}";
		assertError(putTerms("{\"agreements\":[" + agreement + "," + agreement + "]}", csrf), 400, "INVALID_INPUT");
		long oldId = jdbc.queryForObject("select min(id) from term_type where not is_social_signup", Long.class);
		for (long id : new long[]{oldId, Long.MAX_VALUE}) {
			assertError(putTerms("{\"agreements\":[{\"termTypeId\":" + id + ",\"version\":\"v1\",\"isAgreed\":true}]}", csrf),
					404, "TERM_TYPE_NOT_FOUND");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"agreements\":[]}", "{\"agreements\":[null]}",
			"{\"agreements\":[{\"termTypeId\":4}]}", "{\"agreements\":[{\"termTypeId\":-1,\"version\":\"v1\",\"isAgreed\":true}]}"})
	void rejectsInvalidRequest(String body) throws Exception {
		assertError(putTerms(body, csrf()), 400, "INVALID_INPUT");
	}

	@Test
	void requiresCsrfButIgnoresUnrelatedBearerToken() throws Exception {
		assertError(putTerms(validBody(false), null), 403, "INVALID_CSRF_TOKEN");
		csrf();
		assertError(putTerms(validBody(false), "incorrect"), 403, "INVALID_CSRF_TOKEN");
		HttpResponse<String> response = browser.send(HttpRequest.newBuilder(uri(PATH))
				.header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf())
				.header("Authorization", "Bearer invalid")
				.PUT(HttpRequest.BodyPublishers.ofString(validBody(false))).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
	}

	@Test
	void missingAndExpiredSessionsCannotAccessTerms() throws Exception {
		cookies.getCookieStore().removeAll();
		assertError(getTerms(), 401, "INVALID_SOCIAL_SESSION");
		setSignupCookie(socialAuthService.createSession(AuthProvider.KAKAO, "expired", MemberRole.EMPLOYER, null));
		jdbc.update("update social_auth_session set expires_at = now() - interval '1 second'");
		assertError(getTerms(), 401, "INVALID_SOCIAL_SESSION");
		assertError(putTerms(validBody(false), csrf()), 401, "INVALID_SOCIAL_SESSION");
	}

	@Test
	void expiryCleanupDeletesConsentAndPreservesValidSession() throws Exception {
		assertThat(putTerms(validBody(false), csrf()).statusCode()).isEqualTo(200);
		jdbc.update("update social_auth_session set expires_at = now() - interval '1 second'");
		socialAuthService.createSession(AuthProvider.KAKAO, "still-valid", MemberRole.STUDENT, null);
		socialAuthService.deleteExpiredSessions();
		assertThat(jdbc.queryForObject("select count(*) from social_auth_session_term_agreement", Long.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from social_auth_session", Long.class)).isEqualTo(1);
	}

	@Test
	void existingSocialMemberCannotStartSignupConsent() throws Exception {
		UserAccount member = users.saveAndFlush(new UserAccount("registered@example.com", MemberRole.STUDENT));
		accounts.saveAndFlush(new AuthAccount(member, AuthProvider.GOOGLE, "new-student", null));
		assertError(getTerms(), 409, "SOCIAL_ACCOUNT_ALREADY_REGISTERED");
		assertError(putTerms(validBody(false), csrf()), 409, "SOCIAL_ACCOUNT_ALREADY_REGISTERED");
	}

	@Test
	void versionedSignupReadsDoNotRelabelExistingConsentAndMismatchPreservesSnapshot() throws Exception {
		assertThat(putTerms(validBody(true),csrf()).statusCode()).isEqualTo(200);
		var before = jdbc.queryForList("select * from social_auth_session_term_agreement order by term_type_id");
		jdbc.update("update term_type set current_version='test-v2' where is_social_signup");
		var terms = JSON.readTree(getTerms().body()).path("terms");
		assertThat(terms.get(0).path("currentVersion").asString()).isEqualTo("test-v2");
		assertThat(terms.get(0).path("agreedVersion").asString()).isEqualTo("v1");
		assertError(putTerms(validBody(true),csrf()),409,"TERM_VERSION_MISMATCH");
		assertThat(jdbc.queryForList("select * from social_auth_session_term_agreement order by term_type_id")).isEqualTo(before);
		jdbc.update("update term_type set current_version=null where is_social_signup");
		assertError(putTerms(validBody(true),csrf()),503,"TERM_VERSION_UNAVAILABLE");
		assertThat(jdbc.queryForList("select * from social_auth_session_term_agreement order by term_type_id")).isEqualTo(before);
	}

	private String validBody(boolean includeOptional) {
		var agreements = new java.util.ArrayList<Map<String, Object>>();
		for (Long id : requiredIds) {
			agreements.add(Map.of("termTypeId", id, "version", "v1", "isAgreed", true));
		}
		if (includeOptional) {
			agreements.add(Map.of("termTypeId", optionalId, "version", "v1", "isAgreed", true));
		}
		return JSON.writeValueAsString(Map.of("agreements", agreements));
	}

	private void setSignupCookie(String token) {
		HttpCookie cookie = new HttpCookie("socialSignup", token);
		cookie.setPath("/api/auth");
		cookie.setVersion(0);
		cookies.getCookieStore().add(uri("/"), cookie);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> getTerms() throws Exception {
		return browser.send(HttpRequest.newBuilder(uri(PATH)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private String csrf() throws Exception {
		HttpResponse<String> response = browser.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		return JSON.readTree(response.body()).path("token").asString();
	}

	private HttpResponse<String> putTerms(String body, String csrf) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(PATH)).header("Content-Type", "application/json");
		if (csrf != null) {
			request.header("X-XSRF-TOKEN", csrf);
		}
		return browser.send(request.PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo(code);
	}
}
