package com.hirelink.domain.auth;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.service.SocialAuthService;
import com.hirelink.domain.auth.service.TokenService;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.member.repository.UserTermAgreementRepository;
import com.hirelink.global.exception.SmsDeliveryException;
import com.hirelink.global.sms.SmsSendRequest;
import com.hirelink.global.sms.SmsSender;
import com.hirelink.support.PostgreSQLIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialSignupPhoneIntegrationTest extends PostgreSQLIntegrationTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String PATH = "/api/auth/social/phone-verifications";
	private static final String PHONE = "01000000000";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
		registry.add("auth.cookie.secure", () -> false);
		registry.add("auth.social.enabled", () -> true);
		registry.add("auth.social.callback-base-url", () -> "http://localhost");
		registry.add("auth.social.frontend-redirect-uri", () -> "http://localhost/social/callback");
		for (String provider : List.of("google", "kakao")) {
			registry.add("auth.social." + provider + ".client-id", () -> provider + "-test-client");
			registry.add("auth.social." + provider + ".client-secret", () -> "test-client-secret");
		}
	}

	@LocalServerPort private int port;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private SocialAuthService socialAuthService;
	@Autowired private UserAccountRepository users;
	@Autowired private AuthAccountRepository accounts;
	@Autowired private TokenService tokenService;
	@Autowired private PasswordEncoder passwordEncoder;
	@MockitoBean private SmsSender smsSender;
	@MockitoSpyBean private UserTermAgreementRepository agreements;

	@BeforeEach
	void prepare() {
		jdbc.execute("truncate table user_account, social_auth_session restart identity cascade");
		doNothing().when(smsSender).send(any(SmsSendRequest.class));
	}

	@ParameterizedTest
	@CsvSource({"GOOGLE,STUDENT", "KAKAO,EMPLOYER"})
	void createsAccountAtomicallyAndPreservesRoleAndConsentTime(String provider, String role) throws Exception {
		Browser browser = signup(AuthProvider.valueOf(provider), "new-user", MemberRole.valueOf(role), true);
		Object expiry = jdbc.queryForObject("select expires_at from social_auth_session", Object.class);
		List<Map<String, Object>> consent = jdbc.queryForList("select term_type_id, is_agreed, agreed_at from social_auth_session_term_agreement order by term_type_id");
		String code = sendCode(browser, "010-0000-0000");
		assertThat(users.count()).isZero();
		assertThat(accounts.count()).isZero();
		assertThat(jdbc.queryForObject("select expires_at from social_auth_session", Object.class)).isEqualTo(expiry);
		assertThat(passwordEncoder.matches(code, jdbc.queryForObject("select code_hash from phone_verification", String.class))).isTrue();
		Instant codeExpiry = jdbc.queryForObject("select expires_at from phone_verification", java.sql.Timestamp.class).toInstant();
		assertThat(codeExpiry).isAfter(Instant.now().plusSeconds(290)).isBefore(Instant.now().plusSeconds(301));
		HttpResponse<String> response = confirm(browser, PHONE, code);
		assertThat(response.statusCode()).isEqualTo(201);
		JsonNode body = JSON.readTree(response.body());
		assertThat(body.path("onboardingRequired").asBoolean()).isTrue();
		assertThat(body.path("role").asString()).isEqualTo(role);
		assertThat(body.path("expiresIn").asLong()).isEqualTo(3600);
		assertThat(jdbc.queryForObject("select role from user_account", String.class)).isEqualTo(role);
		assertThat(jdbc.queryForObject("select phone_number from user_account", String.class)).isEqualTo(PHONE);
		assertThat(jdbc.queryForObject("select phone_verified_at from user_account", Object.class)).isNotNull();
		assertThat(jdbc.queryForObject("select email from user_account", String.class)).isNull();
		assertThat(jdbc.queryForList("select term_type_id, is_agreed, agreed_at from user_term_agreement order by term_type_id")).isEqualTo(consent);
		assertThat(jdbc.queryForObject("select provider from auth_account", String.class)).isEqualTo(provider);
		assertThat(jdbc.queryForObject("select password_hash from auth_account", String.class)).isNull();
		assertThat(count("social_auth_session")).isZero();
		assertThat(count("phone_verification")).isZero();
		assertThat(count("social_auth_session_term_agreement")).isZero();
		assertThat(count("refresh_token")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select user_id from phone_verification_send_attempt", Long.class)).isEqualTo(body.path("userId").asLong());
		assertThat(response.headers().allValues("Set-Cookie")).anyMatch(v -> v.startsWith("socialSignup=") && v.contains("Max-Age=0"));
		assertThat(response.headers().allValues("Set-Cookie")).anyMatch(v -> v.startsWith("refreshToken=") && v.contains("HttpOnly") && v.contains("Max-Age=1209600"));
		cookie(browser, "socialSignup", browser.token());
		assertError(confirm(browser, PHONE, code), 401, "INVALID_SOCIAL_SESSION");
		String loginSession = socialAuthService.createSession(AuthProvider.valueOf(provider), "new-user", MemberRole.valueOf(role), null);
		assertThat(socialAuthService.getResult(loginSession).response().status().name()).isEqualTo("LOGIN");
		assertThat(users.count()).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({"STUDENT,EMPLOYER,LOCAL", "EMPLOYER,STUDENT,GOOGLE", "STUDENT,STUDENT,KAKAO"})
	void duplicatePhoneClearsOnlyCurrentSignupAndPreservesExistingLogin(String existingRole, String newRole, String provider) throws Exception {
		UserAccount existing = new UserAccount("existing@example.com", MemberRole.valueOf(existingRole));
		existing.verifyPhoneNumber(PHONE, Instant.now());
		users.saveAndFlush(existing);
		accounts.saveAndFlush(new AuthAccount(existing, AuthProvider.valueOf(provider), "existing-identity", null));
		AuthResult login = tokenService.issueTokens(existing);
		List<Map<String, Object>> oldUsers = jdbc.queryForList("select * from user_account");
		List<Map<String, Object>> oldAccounts = jdbc.queryForList("select * from auth_account");
		List<Map<String, Object>> oldTokens = jdbc.queryForList("select * from refresh_token");
		Browser other = signup(AuthProvider.KAKAO, "other-signup", MemberRole.STUDENT, true);
		sendCode(other, "01000000001");
		Browser browser = signup(AuthProvider.GOOGLE, "duplicate", MemberRole.valueOf(newRole), true);
		cookie(browser, "refreshToken", login.refreshToken());
		String code = sendCode(browser, PHONE);
		HttpResponse<String> response = confirm(browser, PHONE, code);
		assertError(response, 409, "DUPLICATE_PHONE_NUMBER");
		assertThat(JSON.readTree(response.body()).path("message").asString()).contains("기존 계정으로 로그인");
		assertThat(response.headers().allValues("Set-Cookie")).hasSize(1).allMatch(v -> v.startsWith("socialSignup=") && v.contains("Max-Age=0"));
		assertThat(browser.cookies().getCookieStore().getCookies()).anyMatch(c -> c.getName().equals("refreshToken") && c.getValue().equals(login.refreshToken()));
		assertThat(jdbc.queryForList("select * from user_account")).isEqualTo(oldUsers);
		assertThat(jdbc.queryForList("select * from auth_account")).isEqualTo(oldAccounts);
		assertThat(jdbc.queryForList("select * from refresh_token")).isEqualTo(oldTokens);
		assertThat(tokenService.refreshAccessToken(login.refreshToken()).accessToken()).isNotBlank();
		assertThat(count("social_auth_session")).isEqualTo(1);
		assertThat(count("phone_verification")).isEqualTo(1);
		assertThat(count("user_term_agreement")).isZero();
		assertThat(count("phone_verification_send_attempt")).isEqualTo(2);
		cookie(browser, "socialSignup", browser.token());
		assertError(confirm(browser, PHONE, code), 401, "INVALID_SOCIAL_SESSION");
		assertThat(get(other, "/api/auth/social/terms").statusCode()).isEqualTo(200);
	}

	@Test
	void rechecksRequiredConsentOnBothEndpoints() throws Exception {
		Browser browser = signup(AuthProvider.GOOGLE, "terms", MemberRole.STUDENT, false);
		assertError(requestCode(browser, PHONE), 400, "REQUIRED_TERMS_NOT_AGREED");
		assertError(confirm(browser, PHONE, "123456"), 400, "REQUIRED_TERMS_NOT_AGREED");
		assertThat(count("phone_verification_send_attempt")).isZero();
		agree(browser);
		String code = sendCode(browser, PHONE);
		jdbc.update("delete from social_auth_session_term_agreement where term_type_id = (select min(id) from term_type where is_required)");
		assertError(confirm(browser, PHONE, code), 400, "REQUIRED_TERMS_NOT_AGREED");
		assertThat(users.count()).isZero();
		assertThat(count("phone_verification")).isEqualTo(1);
	}

	@Test
	void storageFailureRollsBackAccountAuthConsentTokensAndConsumption() throws Exception {
		Browser browser = signup(AuthProvider.GOOGLE, "rollback", MemberRole.STUDENT, true);
		String code = sendCode(browser, PHONE);
		doThrow(new IllegalStateException("Storage unavailable")).when(agreements).save(any());
		HttpResponse<String> response = confirm(browser, PHONE, code);
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(users.count()).isZero();
		assertThat(accounts.count()).isZero();
		assertThat(count("user_term_agreement")).isZero();
		assertThat(count("refresh_token")).isZero();
		assertThat(count("phone_verification")).isEqualTo(1);
		assertThat(count("social_auth_session")).isEqualTo(1);
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
	}

	@Test
	void validatesCookiesCsrfAndInputsWithoutUsingBearer() throws Exception {
		Browser browser = signup(AuthProvider.GOOGLE, "security", MemberRole.STUDENT, true);
		assertError(post(browser, PATH, Map.of("phoneNumber", PHONE), null), 403, "INVALID_CSRF_TOKEN");
		assertError(post(browser, PATH + "/verify", Map.of("phoneNumber", PHONE, "code", "123456"), "incorrect"), 403, "INVALID_CSRF_TOKEN");
		assertError(requestCode(browser, "01100000000"), 400, "INVALID_INPUT");
		assertError(confirm(browser, PHONE, "12345"), 400, "INVALID_INPUT");
		assertThat(requestCode(browser, PHONE).statusCode()).isEqualTo(200);
		browser.cookies().getCookieStore().removeAll();
		String csrf = csrf(browser.client());
		assertError(post(browser, PATH, Map.of("phoneNumber", PHONE), csrf), 401, "INVALID_SOCIAL_SESSION");
		assertError(post(browser, PATH + "/verify", Map.of("phoneNumber", PHONE, "code", "123456"), csrf), 401, "INVALID_SOCIAL_SESSION");
	}

	@Test
	void checksBothExpiryTimesAndCleansOnlyExpiredSignupData() throws Exception {
		Browser browser = signup(AuthProvider.GOOGLE, "expiry", MemberRole.STUDENT, true);
		String code = sendCode(browser, PHONE);
		assertError(confirm(browser, "01000000001", code), 400, "INVALID_VERIFICATION_CODE");
		jdbc.update("update phone_verification set expires_at = now() - interval '1 second'");
		assertError(confirm(browser, PHONE, code), 400, "INVALID_VERIFICATION_CODE");
		jdbc.update("update phone_verification set expires_at = now() + interval '300 seconds'");
		jdbc.update("update social_auth_session set expires_at = now() - interval '1 second'");
		assertError(confirm(browser, PHONE, code), 401, "INVALID_SOCIAL_SESSION");
		assertError(requestCode(browser, PHONE), 401, "INVALID_SOCIAL_SESSION");
		socialAuthService.deleteExpiredSessions();
		assertThat(count("phone_verification")).isZero();
		assertThat(count("social_auth_session_term_agreement")).isZero();
		assertThat(count("phone_verification_send_attempt")).isEqualTo(1);
		assertThat(users.count()).isZero();
	}

	@Test
	void restartInvalidatesOldProgressButPreservesIdentityAndPhoneSendLimits() throws Exception {
		Browser browser = signup(AuthProvider.GOOGLE, "restart", MemberRole.STUDENT, true);
		String code = sendCode(browser, PHONE);
		Browser restarted = signup(AuthProvider.GOOGLE, "restart", MemberRole.EMPLOYER, true);
		assertError(confirm(browser, PHONE, code), 401, "INVALID_SOCIAL_SESSION");
		assertThat(count("social_auth_session")).isEqualTo(1);
		assertThat(count("phone_verification")).isZero();
		assertError(requestCode(restarted, "01000000001"), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		assertError(requestCode(restarted, PHONE), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		HttpResponse<String> start = get(restarted, "/api/auth/social/kakao?role=STUDENT");
		assertThat(start.statusCode()).isEqualTo(302);
		assertThat(count("social_auth_session")).isZero();
		assertThat(count("social_auth_session_term_agreement")).isZero();
		assertThat(count("phone_verification_send_attempt")).isEqualTo(1);
		assertThat(users.count()).isZero();
	}

	@Test
	void failuresSurviveResendAndBlockUntilCodeExpiry() throws Exception {
		Browser browser = signup(AuthProvider.GOOGLE, "failures", MemberRole.STUDENT, true);
		String code = sendCode(browser, PHONE);
		String wrong = code.equals("000000") ? "000001" : "000000";
		assertError(confirm(browser, PHONE, wrong), 400, "INVALID_VERIFICATION_CODE");
		ageAttempts(61);
		String next = sendCode(browser, PHONE);
		assertThat(next).isNotEqualTo(code);
		assertThat(jdbc.queryForObject("select failed_attempts from phone_verification", Integer.class)).isEqualTo(1);
		for (int i = 0; i < 3; i++) assertError(confirm(browser, PHONE, code), 400, "INVALID_VERIFICATION_CODE");
		assertError(confirm(browser, PHONE, code), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		assertError(confirm(browser, PHONE, next), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		ageAttempts(61);
		assertError(requestCode(browser, "01000000001"), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		jdbc.update("update phone_verification set expires_at = now() - interval '1 second'");
		sendCode(browser, PHONE);
		assertThat(jdbc.queryForObject("select failed_attempts from phone_verification", Integer.class)).isZero();
	}

	@Test
	void smsFailureCountsWithoutReplacingPreviousCode() throws Exception {
		Browser browser = signup(AuthProvider.GOOGLE, "sms-failure", MemberRole.STUDENT, true);
		String code = sendCode(browser, PHONE);
		String hash = jdbc.queryForObject("select code_hash from phone_verification", String.class);
		ageAttempts(61);
		doThrow(new SmsDeliveryException()).when(smsSender).send(any(SmsSendRequest.class));
		assertError(requestCode(browser, PHONE), 500, "SMS_DELIVERY_FAILED");
		assertThat(count("phone_verification_send_attempt")).isEqualTo(2);
		assertThat(jdbc.queryForObject("select code_hash from phone_verification", String.class)).isEqualTo(hash);
		assertError(requestCode(browser, PHONE), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		assertThat(confirm(browser, PHONE, code).statusCode()).isEqualTo(201);
	}

	@Test
	void hourlyLimitFollowsIdentityAcrossPhoneChanges() throws Exception {
		Browser browser = signup(AuthProvider.GOOGLE, "hourly", MemberRole.STUDENT, true);
		for (int i = 0; i < 5; i++) {
			assertThat(requestCode(browser, "0100000000" + i).statusCode()).isEqualTo(200);
			ageAttempts(61);
		}
		assertError(requestCode(browser, "01000000009"), 429, "PHONE_VERIFICATION_RATE_LIMIT");
	}

	@Test
	void sharesPhoneLimitWithExistingMemberAndTransfersAccountLimitAfterSignup() throws Exception {
		UserAccount existing = users.saveAndFlush(new UserAccount("local@example.com", MemberRole.STUDENT));
		String accessToken = tokenService.issueTokens(existing).accessToken();
		HttpResponse<String> sent = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/auth/phone-verifications"))
				.header("Authorization", "Bearer " + accessToken).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("phoneNumber", PHONE)))).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(sent.statusCode()).isEqualTo(200);
		Browser browser = signup(AuthProvider.GOOGLE, "shared", MemberRole.EMPLOYER, true);
		assertError(requestCode(browser, PHONE), 429, "PHONE_VERIFICATION_RATE_LIMIT");
		ageAttempts(61);
		String code = sendCode(browser, PHONE);
		JsonNode signedUp = JSON.readTree(confirm(browser, PHONE, code).body());
		HttpResponse<String> limited = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/auth/phone-verifications"))
				.header("Authorization", "Bearer " + signedUp.path("accessToken").asString()).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("phoneNumber", "01000000001")))).build(), HttpResponse.BodyHandlers.ofString());
		assertError(limited, 429, "PHONE_VERIFICATION_RATE_LIMIT");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void simultaneousConfirmationsCannotReuseCodeOrCreateDuplicatePhone(boolean sameSession) throws Exception {
		Browser first = signup(AuthProvider.GOOGLE, "first", MemberRole.STUDENT, true);
		String firstCode = sendCode(first, PHONE);
		Browser second = sameSession ? first : signup(AuthProvider.KAKAO, "second", MemberRole.EMPLOYER, true);
		ageAttempts(61);
		String secondCode = sameSession ? firstCode : sendCode(second, PHONE);
		CompletableFuture<HttpResponse<String>> one = first.client().sendAsync(confirmRequest(first, PHONE, firstCode), HttpResponse.BodyHandlers.ofString());
		CompletableFuture<HttpResponse<String>> two = second.client().sendAsync(confirmRequest(second, PHONE, secondCode), HttpResponse.BodyHandlers.ofString());
		assertThat(List.of(one.get(20, TimeUnit.SECONDS).statusCode(), two.get(20, TimeUnit.SECONDS).statusCode()))
				.containsExactlyInAnyOrder(201, sameSession ? 401 : 409);
		assertThat(users.count()).isEqualTo(1);
		assertThat(accounts.count()).isEqualTo(1);
		assertThat(count("user_term_agreement")).isEqualTo(4);
		assertThat(count("social_auth_session")).isZero();
	}

	@Test
	void concurrentExistingMemberConfirmationAndSignupSharePhoneUniqueness() throws Exception {
		UserAccount existing = users.saveAndFlush(new UserAccount("existing@example.com", MemberRole.STUDENT));
		String accessToken = tokenService.issueTokens(existing).accessToken();
		HttpClient memberClient = HttpClient.newHttpClient();
		HttpRequest memberSend = HttpRequest.newBuilder(uri("/api/auth/phone-verifications"))
				.header("Authorization", "Bearer " + accessToken).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("phoneNumber", PHONE)))).build();
		assertThat(memberClient.send(memberSend, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
		ArgumentCaptor<SmsSendRequest> captured = ArgumentCaptor.forClass(SmsSendRequest.class);
		verify(smsSender).send(captured.capture());
		String memberCode = verificationCode(captured.getValue());
		ageAttempts(61);
		Browser browser = signup(AuthProvider.GOOGLE, "competing-signup", MemberRole.EMPLOYER, true);
		String signupCode = sendCode(browser, PHONE);
		HttpRequest memberConfirm = HttpRequest.newBuilder(uri("/api/auth/phone-verifications/verify"))
				.header("Authorization", "Bearer " + accessToken).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("phoneNumber", PHONE, "code", memberCode)))).build();
		var member = memberClient.sendAsync(memberConfirm, HttpResponse.BodyHandlers.ofString());
		var signup = browser.client().sendAsync(confirmRequest(browser, PHONE, signupCode), HttpResponse.BodyHandlers.ofString());
		int memberStatus = member.get(20, TimeUnit.SECONDS).statusCode();
		int signupStatus = signup.get(20, TimeUnit.SECONDS).statusCode();
		assertThat(List.of(memberStatus, signupStatus)).isIn(List.of(200, 409), List.of(409, 201));
		assertThat(jdbc.queryForObject("select count(*) from user_account where phone_number = ?", Integer.class, PHONE)).isEqualTo(1);
		assertThat(count("social_auth_session")).isZero();
	}

	@Test
	void simultaneousSendsRespectSharedPhoneCooldown() throws Exception {
		Browser first = signup(AuthProvider.GOOGLE, "send-first", MemberRole.STUDENT, true);
		Browser second = signup(AuthProvider.KAKAO, "send-second", MemberRole.STUDENT, true);
		var one = first.client().sendAsync(postRequest(first, PATH, Map.of("phoneNumber", PHONE), first.csrf()), HttpResponse.BodyHandlers.ofString());
		var two = second.client().sendAsync(postRequest(second, PATH, Map.of("phoneNumber", PHONE), second.csrf()), HttpResponse.BodyHandlers.ofString());
		assertThat(List.of(one.get(20, TimeUnit.SECONDS).statusCode(), two.get(20, TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200, 429);
		assertThat(count("phone_verification_send_attempt")).isEqualTo(1);
	}

	private Browser signup(AuthProvider provider, String subject, MemberRole role, boolean consent) throws Exception {
		String token = socialAuthService.createSession(provider, subject, role, null);
		CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
		Browser browser = new Browser(client, cookies, token, csrf(client));
		cookie(browser, "socialSignup", token);
		if (consent) agree(browser);
		return browser;
	}

	private void agree(Browser browser) throws Exception {
		List<Map<String, Object>> values = jdbc.queryForList("select id from term_type where is_social_signup and is_required", Long.class)
				.stream().map(id -> Map.<String, Object>of("termTypeId", id, "isAgreed", true)).toList();
		HttpRequest request = HttpRequest.newBuilder(uri("/api/auth/social/terms")).header("Content-Type", "application/json")
				.header("X-XSRF-TOKEN", browser.csrf()).PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("agreements", values)))).build();
		assertThat(browser.client().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
	}

	private void cookie(Browser browser, String name, String value) {
		HttpCookie cookie = new HttpCookie(name, value);
		cookie.setVersion(0);
		cookie.setPath("/api/auth");
		browser.cookies().getCookieStore().add(uri("/"), cookie);
	}

	private String csrf(HttpClient client) throws Exception {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
		return JSON.readTree(response.body()).path("token").asString();
	}

	private String sendCode(Browser browser, String phone) throws Exception {
		HttpResponse<String> response = requestCode(browser, phone);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(JSON.readTree(response.body()).path("expiresIn").asInt()).isEqualTo(300);
		ArgumentCaptor<SmsSendRequest> requests = ArgumentCaptor.forClass(SmsSendRequest.class);
		verify(smsSender, atLeastOnce()).send(requests.capture());
		return verificationCode(requests.getValue());
	}

	private String verificationCode(SmsSendRequest request) {
		return request.message().substring("[HIRELINK] 인증번호 [".length(), request.message().length() - 1);
	}

	private HttpResponse<String> requestCode(Browser browser, String phone) throws Exception {
		return post(browser, PATH, Map.of("phoneNumber", phone), browser.csrf());
	}

	private HttpRequest confirmRequest(Browser browser, String phone, String code) {
		return postRequest(browser, PATH + "/verify", Map.of("phoneNumber", phone, "code", code), browser.csrf());
	}

	private HttpResponse<String> confirm(Browser browser, String phone, String code) throws Exception {
		return browser.client().send(confirmRequest(browser, phone, code), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(Browser browser, String path) throws Exception {
		return browser.client().send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(Browser browser, String path, Object body, String csrf) throws Exception {
		return browser.client().send(postRequest(browser, path, body, csrf), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest postRequest(Browser browser, String path, Object body, String csrf) {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
				.header("Authorization", "Bearer ignored-invalid-token");
		if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
		return request.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
	}

	private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
	private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
	private void ageAttempts(int seconds) { jdbc.update("update phone_verification_send_attempt set requested_at = requested_at - make_interval(secs => ?)", seconds); }
	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo(code);
	}
	private record Browser(HttpClient client, CookieManager cookies, String token, String csrf) { }
}
