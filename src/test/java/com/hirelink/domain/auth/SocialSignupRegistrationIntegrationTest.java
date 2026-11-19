package com.hirelink.domain.auth;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.hirelink.domain.auth.client.ApickRegistrationClient;
import com.hirelink.domain.auth.client.RegistrationOcrClient;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.service.RegistrationCipher;
import com.hirelink.domain.auth.service.SocialAuthService;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.support.PostgreSQLIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialSignupRegistrationIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String PATH = "/api/auth/social/registration/";
	private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};
	private static final String NUMBER = "0001015000000";
	private static final ObjectMapper JSON = new ObjectMapper();
	@LocalServerPort int port;
	@Autowired JdbcTemplate jdbc;
	@Autowired SocialAuthService auth;
	@Autowired RegistrationCipher cipher;
	@MockitoBean RegistrationOcrClient ocr;
	@MockitoBean ApickRegistrationClient apick;
	private CookieManager cookies;
	private HttpClient browser;
	private String csrf;
	private String token;
	private LocalDate today;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
		registry.add("auth.cookie.secure", () -> false);
		registry.add("auth.social.enabled", () -> true);
		registry.add("auth.social.callback-base-url", () -> "http://localhost");
		registry.add("auth.social.frontend-redirect-uri", () -> "http://localhost/social/callback");
		registry.add("registration-verification.enabled", () -> true);
		registry.add("registration-verification.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
		for (String provider : new String[]{"google", "kakao"}) {
			registry.add("auth.social." + provider + ".client-id", () -> "test-client");
			registry.add("auth.social." + provider + ".client-secret", () -> "test-secret");
		}
	}

	@BeforeEach
	void setup() throws Exception {
		jdbc.execute("TRUNCATE TABLE user_account, social_auth_session RESTART IDENTITY CASCADE");
		cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		browser = HttpClient.newBuilder().cookieHandler(cookies).build();
		setSession(MemberRole.STUDENT);
		csrf = JSON.readTree(get("/api/auth/csrf").body()).path("token").asString();
		today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		when(ocr.recognize(any(), anyString(), eq(true))).thenAnswer(invocation -> frontResult());
		when(ocr.recognize(any(), anyString(), eq(false))).thenAnswer(invocation -> backResult(today, "00000000001"));
	}

	@Test
	void encryptsTemporaryDataAndDeletesNumberAfterSuccessfulUnexpiredVerification() throws Exception {
		agree();
		Object expires = jdbc.queryForObject("select expires_at from social_auth_session", Object.class);
		Object agreed = jdbc.queryForObject("select agreed_at from social_auth_session_term_agreement a join term_type t on t.id=a.term_type_id where t.is_student_registration", Object.class);
		HttpResponse<String> front = upload("front");
		assertThat(front.statusCode()).as(front.body()).isEqualTo(200);
		assertThat(front.body()).doesNotContain(NUMBER).contains("000101-*******");
		assertThat(front.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(payload()).startsWith("v1.").doesNotContain(NUMBER, "TEST STUDENT");
		assertThat(decrypted().path("registrationNumber").asString()).isEqualTo(NUMBER);
		assertThat(jdbc.queryForObject("select count(*) from user_account", Long.class)).isZero();
		HttpResponse<String> back = upload("back");
		assertThat(back.statusCode()).as(back.body()).isEqualTo(200);
		JsonNode result = JSON.readTree(back.body());
		assertThat(result.path("cardVerified").asBoolean()).isTrue();
		assertThat(result.path("requiresStayDocument").asBoolean()).isFalse();
		assertThat(result.path("stayExpiresOn").asString()).isEqualTo(today.toString());
		assertThat(decrypted().has("registrationNumber")).isFalse();
		assertThat(decrypted().path("nationalityCode").asString()).isEqualTo("VN");
		assertThat(jdbc.queryForObject("select expires_at from social_auth_session", Object.class)).isEqualTo(expires);
		assertThat(jdbc.queryForObject("select agreed_at from social_auth_session_term_agreement a join term_type t on t.id=a.term_type_id where t.is_student_registration", Object.class)).isEqualTo(agreed);
		verify(apick).verify(NUMBER, LocalDate.of(2020, 1, 1), "00000000001");
		assertError(upload("back"), 409, "REGISTRATION_FRONT_REQUIRED");
	}

	@Test
	void expiredCardRequiresPdfAndRetainsEncryptedNumberOnlyInSession() throws Exception {
		agree();
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), "00000000001"));
		assertThat(upload("front").statusCode()).isEqualTo(200);
		HttpResponse<String> back = upload("back");
		assertThat(back.statusCode()).isEqualTo(200);
		assertThat(JSON.readTree(back.body()).path("requiresStayDocument").asBoolean()).isTrue();
		assertThat(decrypted().path("registrationNumber").asString()).isEqualTo(NUMBER);
		assertThat(payload()).doesNotContain(NUMBER);
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class)).isNull();
	}

	@Test
	void failedRecapturesInvalidatePreviousResultsAndSanitizeErrors() throws Exception {
		agree();
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), "00000000001"));
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		doThrow(new SocialAuthException(HttpStatus.BAD_GATEWAY, "REGISTRATION_VERIFICATION_FAILED", "다시 시도해주세요."))
				.when(apick).verify(anyString(), any(), anyString());
		assertError(upload("back"), 502, "REGISTRATION_VERIFICATION_FAILED");
		assertThat(jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class)).isNull();
		when(ocr.recognize(any(), anyString(), eq(true))).thenThrow(RegistrationOcrClient.unreadable());
		assertError(upload("front"), 422, "UNREADABLE_REGISTRATION");
		assertThat(payload()).isNull();
		assertError(upload("back"), 409, "REGISTRATION_FRONT_REQUIRED");
	}

	@Test
	void rejectsMissingConsentCsrfSessionAndEmployerBeforeExternalProcessing() throws Exception {
		assertError(upload("front"), 400, "REQUIRED_TERMS_NOT_AGREED");
		agree();
		jdbc.update("delete from social_auth_session_term_agreement where term_type_id in (select id from term_type where is_student_registration)");
		assertError(upload("front"), 400, "REQUIRED_TERMS_NOT_AGREED");
		assertError(send(uploadRequest("front", PNG, "image/png", null, "", false)), 403, "INVALID_CSRF_TOKEN");
		setSession(MemberRole.EMPLOYER);
		assertThat(JSON.readTree(get("/api/auth/social/terms").body()).path("terms").size()).isEqualTo(4);
		assertError(upload("front"), 403, "FORBIDDEN");
		cookies.getCookieStore().removeAll();
		csrf = JSON.readTree(get("/api/auth/csrf").body()).path("token").asString();
		assertError(upload("front"), 401, "INVALID_SOCIAL_SESSION");
		verifyNoInteractions(ocr, apick);
	}

	@Test
	void acceptsSeparateConsentOnlyForStudentAndRejectsMissingIdentityConsent() throws Exception {
		JsonNode terms = JSON.readTree(get("/api/auth/social/terms").body()).path("terms");
		assertThat(terms.size()).isEqualTo(5);
		assertThat(terms.get(4).path("termTypeName").asString()).isEqualTo("고유식별정보 수집·이용");
		assertThat(terms.get(4).path("isRequired").asBoolean()).isTrue();
		List<Map<String, Object>> incomplete = jdbc.queryForList("select id from term_type where is_social_signup and is_required and not is_student_registration", Long.class)
				.stream().map(id -> Map.<String, Object>of("termTypeId", id, "isAgreed", true)).toList();
		assertError(saveTerms(incomplete), 400, "REQUIRED_TERMS_NOT_AGREED");
		assertThat(jdbc.queryForObject("select count(*) from social_auth_session_term_agreement", Long.class)).isZero();
	}

	@Test
	void rejectsPdfExtraFieldsMultipleFilesAndOversizedImages() throws Exception {
		agree();
		assertError(send(uploadRequest("front", "%PDF-test".getBytes(StandardCharsets.UTF_8), "application/pdf", csrf, "", false)), 415, "UNSUPPORTED_FILE_TYPE");
		assertError(send(uploadRequest("front", new byte[]{1}, "image/png", csrf, "", false)), 415, "UNSUPPORTED_FILE_TYPE");
		assertError(send(uploadRequest("front", new byte[0], "image/png", csrf, "", false)), 400, "INVALID_INPUT");
		assertError(send(uploadRequest("front", PNG, "image/png", csrf, "--boundary\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nmodified\r\n", false)), 400, "INVALID_INPUT");
		assertError(send(uploadRequest("front", PNG, "image/png", csrf, "", true)), 400, "INVALID_INPUT");
		assertError(send(uploadRequest("front", new byte[5 * 1024 * 1024 + 1], "image/png", csrf, "", false)), 413, "FILE_TOO_LARGE");
		verifyNoInteractions(ocr, apick);
	}

	@Test
	void expiryCleanupAndSignupRestartDeleteAssociatedCiphertext() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		jdbc.update("update social_auth_session set expires_at = now() - interval '1 second'");
		assertError(upload("back"), 401, "INVALID_SOCIAL_SESSION");
		auth.deleteExpiredSessions();
		assertThat(jdbc.queryForObject("select count(*) from social_signup_registration", Long.class)).isZero();
		setSession(MemberRole.STUDENT);
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		setSession(MemberRole.STUDENT);
		assertThat(jdbc.queryForObject("select count(*) from social_signup_registration", Long.class)).isZero();
	}

	@Test
	void externalRequestCannotCommitSuccessAfterSessionExpires() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		Instant expires = Instant.now().plusSeconds(2);
		jdbc.update("update social_auth_session set expires_at = ?", java.sql.Timestamp.from(expires));
		doAnswer(invocation -> {
			long wait = expires.toEpochMilli() - System.currentTimeMillis() + 100;
			if (wait > 0) { Thread.sleep(wait); }
			return null;
		}).when(apick).verify(anyString(), any(), anyString());
		assertError(upload("back"), 401, "INVALID_SOCIAL_SESSION");
		assertThat(payload()).isNull();
		assertThat(jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class)).isNull();
	}

	@Test
	void sameSessionRecapturesAreSerializedAndCannotOverwriteNewerResult() throws Exception {
		agree();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		when(ocr.recognize(any(), anyString(), eq(true))).thenAnswer(invocation -> {
			int call = calls.incrementAndGet();
			if (call == 1) {
				entered.countDown();
				if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException(); }
			}
			return JSON.readTree(frontResult().toString().replace("TEST STUDENT", "STUDENT " + call));
		});
		var one = browser.sendAsync(uploadRequest("front", PNG, "image/png", csrf, "", false), HttpResponse.BodyHandlers.ofString());
		assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
		var two = browser.sendAsync(uploadRequest("front", PNG, "image/png", csrf, "", false), HttpResponse.BodyHandlers.ofString());
		release.countDown();
		assertThat(one.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(two.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(decrypted().path("name").asString()).isEqualTo("STUDENT 2");
	}

	@Test
	void oldCardsCanOmitSerialButRecentCardsCannot() throws Exception {
		agree();
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today, null));
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertError(upload("back"), 422, "UNREADABLE_REGISTRATION");
		verifyNoInteractions(apick);
		when(ocr.recognize(any(), anyString(), eq(true))).thenReturn(JSON.readTree(frontResult().toString().replace("2020-01-01", "2009-01-01")));
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		verify(apick).verify(NUMBER, LocalDate.of(2009, 1, 1), null);
	}

	@Test
	void openApiExposesOnlyImageInputAndMaskedFrontResponse() throws Exception {
		JsonNode document = JSON.readTree(get("/v3/api-docs").body());
		for (String side : List.of("front", "back")) {
			JsonNode operation = document.path("paths").path(PATH + side).path("post");
			assertThat(operation.path("security").isEmpty()).isTrue();
			JsonNode schema = resolve(document, operation.path("requestBody").path("content").path("multipart/form-data").path("schema"));
			assertThat(schema.path("properties").propertyNames()).containsExactly("registrationFile");
			assertThat(schema.path("properties").path("registrationFile").path("format").asString()).isEqualTo("binary");
			assertThat(operation.path("responses").propertyNames()).contains("200", "400", "401", "403", "409", "413", "415", "422", "502", "503", "500");
			assertThat(operation.path("parameters").toString()).contains("socialSignup", "X-XSRF-TOKEN", "XSRF-TOKEN");
		}
		JsonNode frontSchema = document.path("components").path("schemas").path("RegistrationFrontResponse").path("properties");
		assertThat(frontSchema.propertyNames()).contains("maskedRegistrationNumber").doesNotContain("registrationNumber");
	}

	private JsonNode resolve(JsonNode document, JsonNode schema) {
		return schema.has("$ref") ? document.at(schema.path("$ref").asString().substring(1)) : schema;
	}
	private JsonNode frontResult() {
		return JSON.readTree("{\"name\":\"TEST STUDENT\",\"nationality\":\"VIETNAM\",\"visaCode\":\"D-2\",\"registrationNumber\":\"" + NUMBER + "\",\"issueDate\":\"2020-01-01\"}");
	}
	private JsonNode backResult(LocalDate date, String serial) {
		return JSON.createObjectNode().put("stayExpiresOn", date.toString()).put("serialNumber", serial);
	}
	private String payload() { return jdbc.queryForObject("select front_payload from social_signup_registration", String.class); }
	private JsonNode decrypted() {
		String hash = jdbc.queryForObject("select token_hash from social_signup_registration", String.class);
		return JSON.readTree(cipher.decrypt(payload(), hash));
	}
	private void setSession(MemberRole role) {
		token = auth.createSession(AuthProvider.GOOGLE, "registration-test", role, null);
		cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("socialSignup"))
				.toList().forEach(c -> cookies.getCookieStore().remove(uri("/"), c));
		HttpCookie cookie = new HttpCookie("socialSignup", token);
		cookie.setPath("/api/auth");
		cookie.setVersion(0);
		cookies.getCookieStore().add(uri("/"), cookie);
	}
	private void agree() throws Exception {
		List<Map<String, Object>> values = jdbc.queryForList("select id from term_type where is_social_signup and is_required", Long.class)
				.stream().map(id -> Map.<String, Object>of("termTypeId", id, "isAgreed", true)).toList();
		HttpResponse<String> response = saveTerms(values);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
	}
	private HttpResponse<String> saveTerms(List<Map<String, Object>> values) throws Exception {
		return send(HttpRequest.newBuilder(uri("/api/auth/social/terms")).header("Content-Type", "application/json")
				.header("X-XSRF-TOKEN", csrf).PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("agreements", values)))).build());
	}
	private HttpResponse<String> upload(String side) throws Exception {
		return send(uploadRequest(side, PNG, "image/png", csrf, "", false));
	}
	private HttpRequest uploadRequest(String side, byte[] image, String kind, String csrfValue, String extra, boolean duplicate) {
		String header = "--boundary\r\nContent-Disposition: form-data; name=\"registrationFile\"; filename=\"capture.png\"\r\nContent-Type: " + kind + "\r\n\r\n";
		byte[] prefix = header.getBytes(StandardCharsets.UTF_8);
		byte[] suffix = ("\r\n" + extra + "--boundary--\r\n").getBytes(StandardCharsets.UTF_8);
		var body = duplicate ? HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
				HttpRequest.BodyPublishers.ofByteArray(image), HttpRequest.BodyPublishers.ofString("\r\n" + header),
				HttpRequest.BodyPublishers.ofByteArray(image), HttpRequest.BodyPublishers.ofByteArray(suffix))
				: HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix), HttpRequest.BodyPublishers.ofByteArray(image), HttpRequest.BodyPublishers.ofByteArray(suffix));
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(PATH + side)).header("Content-Type", "multipart/form-data; boundary=boundary")
				.header("Authorization", "Bearer ignored-for-cookie-auth").POST(body);
		if (csrfValue != null) { request.header("X-XSRF-TOKEN", csrfValue); }
		return request.build();
	}
	private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
	private HttpResponse<String> get(String path) throws Exception { return send(HttpRequest.newBuilder(uri(path)).GET().build()); }
	private HttpResponse<String> send(HttpRequest request) throws Exception { return browser.send(request, HttpResponse.BodyHandlers.ofString()); }
	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo(code);
		assertThat(response.body()).doesNotContain(NUMBER);
	}
}
