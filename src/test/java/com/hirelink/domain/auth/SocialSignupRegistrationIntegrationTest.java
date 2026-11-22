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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.mockito.ArgumentCaptor;

import com.hirelink.domain.auth.client.ApickRegistrationClient;
import com.hirelink.domain.auth.client.RegistrationOcrClient;
import com.hirelink.domain.auth.dto.RegistrationFrontResponse;
import com.hirelink.domain.auth.dto.RegistrationVerificationResponse;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.service.RegistrationCipher;
import com.hirelink.domain.auth.service.SocialAuthService;
import com.hirelink.domain.auth.service.TokenService;
import com.hirelink.domain.auth.repository.PhoneVerificationSendAttemptRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.sms.SmsSender;
import com.hirelink.global.sms.SmsSendRequest;
import com.hirelink.support.PostgreSQLIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialSignupRegistrationIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String PATH = "/api/auth/social/registration/";
	private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};
	private static final String NUMBER = "0001015000000";
	private static final String DISPLAY_NUMBER = "000101-5000000";
	private static final String SERIAL = "00000000001";
	private static final ObjectMapper JSON = new ObjectMapper();
	@LocalServerPort int port;
	@Autowired JdbcTemplate jdbc;
	@Autowired SocialAuthService auth;
	@Autowired RegistrationCipher cipher;
	@MockitoBean RegistrationOcrClient ocr;
	@MockitoBean ApickRegistrationClient apick;
	@MockitoBean SmsSender smsSender;
	@MockitoSpyBean StudentRegistrationVerificationRepository verifiedRegistrations;
	@MockitoSpyBean PhoneVerificationSendAttemptRepository attempts;
	@MockitoSpyBean TokenService tokenService;
	@Autowired UserAccountRepository users;
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
		JsonNode frontBody = JSON.readTree(front.body());
		assertThat(frontBody.propertyNames()).containsExactlyInAnyOrder("name", "nationalityCode", "visaCode", "registrationNumber", "issueDate", "signupExpiresAt");
		assertThat(frontBody.path("registrationNumber").asString()).isEqualTo(DISPLAY_NUMBER);
		assertThat(frontBody.path("name").asString()).isEqualTo("TEST STUDENT");
		assertThat(frontBody.path("nationalityCode").asString()).isEqualTo("VN");
		assertThat(frontBody.path("visaCode").asString()).isEqualTo("D-2");
		assertThat(frontBody.path("issueDate").asString()).isEqualTo("2020-01-01");
		assertThat(Instant.parse(frontBody.path("signupExpiresAt").asString())).isEqualTo(((java.sql.Timestamp) expires).toInstant());
		assertThat(front.headers().firstValue("Content-Type")).hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
		assertThat(front.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(payload()).startsWith("v1.").doesNotContain(NUMBER, DISPLAY_NUMBER, "TEST STUDENT");
		assertThat(decrypted().path("registrationNumber").asString()).isEqualTo(NUMBER);
		assertThat(jdbc.queryForObject("select count(*) from user_account", Long.class)).isZero();
		HttpResponse<String> back = upload("back");
		assertVerificationResponse(back, SERIAL, today, false, false);
		assertThat(back.body()).doesNotContain(NUMBER, DISPLAY_NUMBER, "registrationNumber");
		JsonNode result = JSON.readTree(back.body());
		assertThat(result.path("cardVerified").asBoolean()).isTrue();
		assertThat(result.path("requiresStayDocument").asBoolean()).isFalse();
		assertThat(result.path("stayExpiresOn").asString()).isEqualTo(today.toString());
		assertThat(decrypted().has("registrationNumber")).isFalse();
		assertThat(decrypted().has("issueDate")).isFalse();
		assertThat(decrypted().has("serialNumber")).isFalse();
		assertThat(decrypted().path("nationalityCode").asString()).isEqualTo("VN");
		assertThat(jdbc.queryForObject("select expires_at from social_auth_session", Object.class)).isEqualTo(expires);
		assertThat(jdbc.queryForObject("select agreed_at from social_auth_session_term_agreement a join term_type t on t.id=a.term_type_id where t.is_student_registration", Object.class)).isEqualTo(agreed);
		verify(apick).verify(NUMBER, LocalDate.of(2020, 1, 1), "00000000001");
		assertError(upload("back"), 409, "REGISTRATION_FRONT_REQUIRED");
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void frontResponseAndValidationErrorsDoNotLogRegistrationNumber(CapturedOutput output) throws Exception {
		agree();
		HttpResponse<String> response = upload("front");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(JSON.readTree(response.body()).path("registrationNumber").asString()).isEqualTo(DISPLAY_NUMBER);
		RegistrationFrontResponse dto = new RegistrationFrontResponse("TEST STUDENT", "VN", "D-2", DISPLAY_NUMBER,
				LocalDate.of(2020, 1, 1), Instant.now());
		assertThat(dto.toString()).isEqualTo("RegistrationFrontResponse[redacted]");
		when(ocr.recognize(any(), anyString(), eq(true)))
				.thenReturn(JSON.readTree(frontResult().toString().replace("D-2", "UNSUPPORTED")));
		assertError(upload("front"), 422, "UNREADABLE_REGISTRATION");
		assertThat(payload()).isNull();
		assertThat(output.getAll()).doesNotContain(NUMBER, DISPLAY_NUMBER);
	}

	@Test
	void frontResponseIsIsolatedToTheCurrentSignupSession() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		String firstHash = jdbc.queryForObject("select token_hash from social_signup_registration", String.class);
		String firstPayload = payload();
		setSession(MemberRole.STUDENT, "other-registration-test");
		assertError(upload("front"), 400, "REQUIRED_TERMS_NOT_AGREED");
		agree();
		assertError(upload("back"), 409, "REGISTRATION_FRONT_REQUIRED");
		String otherNumber = "0101016000001";
		when(ocr.recognize(any(), anyString(), eq(true)))
				.thenReturn(JSON.readTree(frontResult().toString().replace(NUMBER, otherNumber)));
		HttpResponse<String> response = upload("front");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(JSON.readTree(response.body()).path("registrationNumber").asString()).isEqualTo("010101-6000001");
		assertThat(response.body()).doesNotContain(NUMBER, DISPLAY_NUMBER);
		assertThat(jdbc.queryForObject("select front_payload from social_signup_registration where token_hash = ?", String.class, firstHash)).isEqualTo(firstPayload);
		String secondHash = jdbc.queryForObject("select token_hash from social_signup_registration where token_hash <> ?", String.class, firstHash);
		String secondPayload = jdbc.queryForObject("select front_payload from social_signup_registration where token_hash = ?", String.class, secondHash);
		assertThat(secondPayload).doesNotContain(NUMBER, DISPLAY_NUMBER, otherNumber, "010101-6000001");
		assertThat(JSON.readTree(cipher.decrypt(secondPayload, secondHash)).path("registrationNumber").asString()).isEqualTo(otherNumber);
	}

	@Test
	void frontCannotReturnTheNumberWhenSessionExpiresDuringOcr() throws Exception {
		agree();
		Instant expires = Instant.now().plusSeconds(2);
		jdbc.update("update social_auth_session set expires_at = ?", java.sql.Timestamp.from(expires));
		when(ocr.recognize(any(), anyString(), eq(true))).thenAnswer(invocation -> {
			long wait = expires.toEpochMilli() - System.currentTimeMillis() + 100;
			if (wait > 0) { Thread.sleep(wait); }
			return frontResult();
		});
		assertError(upload("front"), 401, "INVALID_SOCIAL_SESSION");
		assertThat(payload()).isNull();
		assertThat(count("user_account")).isZero();
		verifyNoInteractions(apick);
	}

	@Test
	void expiredCardRequiresPdfAndRetainsEncryptedNumberOnlyInSession() throws Exception {
		agree();
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), "00000000001"));
		assertThat(upload("front").statusCode()).isEqualTo(200);
		HttpResponse<String> back = upload("back");
		assertVerificationResponse(back, SERIAL, today.minusDays(1), false, true);
		assertThat(JSON.readTree(back.body()).path("requiresStayDocument").asBoolean()).isTrue();
		assertThat(decrypted().path("registrationNumber").asString()).isEqualTo(NUMBER);
		assertThat(decrypted().path("serialNumber").asString()).isEqualTo(SERIAL);
		assertThat(payload()).doesNotContain(NUMBER, SERIAL);
		assertThat(decrypted().has("issueDate")).isFalse();
		assertError(upload("back"), 409, "REGISTRATION_FRONT_REQUIRED");
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(decrypted().has("serialNumber")).isFalse();
		assertThat(jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class)).isNull();
	}

	@Test
	void failedRecapturesInvalidatePreviousResultsAndSanitizeErrors() throws Exception {
		agree();
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), "00000000001"));
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		assertThat(upload("front").statusCode()).isEqualTo(200);
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
		assertError(upload("front"), 401, "INVALID_SOCIAL_SESSION");
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
		assertVerificationResponse(upload("back"), null, today, false, false);
		assertThat(decrypted().has("serialNumber")).isFalse();
		verify(apick).verify(NUMBER, LocalDate.of(2009, 1, 1), null);
	}

	@Test
	void openApiExposesOnlyImageInputAndFullFrontResponse() throws Exception {
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
		JsonNode frontSchema = document.path("components").path("schemas").path("RegistrationFrontResponse");
		assertThat(frontSchema.path("properties").propertyNames()).contains("registrationNumber").doesNotContain("maskedRegistrationNumber");
		assertThat(frontSchema.path("required").toString()).contains("registrationNumber");
		JsonNode number = frontSchema.path("properties").path("registrationNumber");
		assertThat(number.path("type").asString()).isEqualTo("string");
		assertThat(number.path("minLength").asInt()).isEqualTo(14);
		assertThat(number.path("maxLength").asInt()).isEqualTo(14);
		assertThat(number.path("example").asString()).isEqualTo(DISPLAY_NUMBER);
		assertThat(DISPLAY_NUMBER.matches(number.path("pattern").asString())).isTrue();
		assertThat(NUMBER.matches(number.path("pattern").asString())).isFalse();
		JsonNode example = document.path("paths").path(PATH + "front").path("post").path("responses")
				.path("200").path("content").path("application/json").path("examples").path("success").path("value");
		if (example.isString()) { example = JSON.readTree(example.asString()); }
		assertThat(example.path("registrationNumber").asString()).isEqualTo(DISPLAY_NUMBER);
		assertThat(example.has("maskedRegistrationNumber")).isFalse();
	}

	@Test
	void pdfSuccessChecksTodayDeletesIdentifiersAndRecaptureResetsDocument() throws Exception {
		preparePdf();
		Object expires = jdbc.queryForObject("select expires_at from social_auth_session", Object.class);
		Object cardVerified = jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class);
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult(NUMBER, today));
		HttpResponse<String> response = uploadPdf();
		assertVerificationResponse(response, SERIAL, today, true, false);
		assertThat(response.body()).doesNotContain(NUMBER, DISPLAY_NUMBER, "registrationNumber", "issueDate");
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(JSON.readTree(response.body()).path("stayDocumentVerified").asBoolean()).isTrue();
		assertThat(JSON.readTree(response.body()).path("stayExpiresOn").asString()).isEqualTo(today.toString());
		assertThat(decrypted().has("registrationNumber")).isFalse();
		assertThat(decrypted().has("issueDate")).isFalse();
		assertThat(decrypted().has("serialNumber")).isFalse();
		assertThat(decrypted().path("name").asString()).isEqualTo("TEST STUDENT");
		assertThat(jdbc.queryForObject("select document_expires_on from social_signup_registration", LocalDate.class)).isEqualTo(today);
		assertThat(jdbc.queryForObject("select document_verified_at from social_signup_registration", Object.class)).isNotNull();
		assertThat(jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class)).isEqualTo(cardVerified);
		assertThat(jdbc.queryForObject("select expires_at from social_auth_session", Object.class)).isEqualTo(expires);
		assertThat(jdbc.queryForObject("select count(*) from user_account", Long.class)).isZero();
		verifyNoInteractions(apick);
		assertError(uploadPdf(), 409, "REGISTRATION_FRONT_REQUIRED");
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select document_verified_at from social_signup_registration", Object.class)).isNull();
		assertThat(jdbc.queryForObject("select document_expires_on from social_signup_registration", Object.class)).isNull();
		assertError(uploadPdf(), 409, "REGISTRATION_BACK_REQUIRED");
	}

	@Test
	void pdfRejectsMissingConsentCardSessionAndUnnecessaryDocumentBeforeOcr() throws Exception {
		assertError(uploadPdf(), 400, "REQUIRED_TERMS_NOT_AGREED");
		agree();
		assertError(uploadPdf(), 409, "REGISTRATION_BACK_REQUIRED");
		assertError(send(uploadRequest("stay-document", "%PDF-test".getBytes(StandardCharsets.UTF_8), "application/pdf", null, "", false)), 403, "INVALID_CSRF_TOKEN");
		verifyNoInteractions(ocr, apick);
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertError(uploadPdf(), 409, "REGISTRATION_BACK_REQUIRED");
		assertThat(upload("back").statusCode()).isEqualTo(200);
		assertError(uploadPdf(), 409, "STAY_DOCUMENT_NOT_REQUIRED");
		verify(ocr, never()).recognizeStayDocument(any());
		setSession(MemberRole.EMPLOYER);
		assertError(uploadPdf(), 403, "FORBIDDEN");
		cookies.getCookieStore().removeAll();
		csrf = JSON.readTree(get("/api/auth/csrf").body()).path("token").asString();
		assertError(uploadPdf(), 401, "INVALID_SOCIAL_SESSION");
	}

	@Test
	void pdfMismatchExpiryAndUnreadableResultsRemainRetryableWithoutVerifying() throws Exception {
		preparePdf();
		String before = payload();
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult("0001016000000", today));
		assertError(uploadPdf(), 422, "STAY_DOCUMENT_MISMATCH");
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult(NUMBER, today.minusDays(1)));
		assertError(uploadPdf(), 422, "STAY_DOCUMENT_EXPIRED");
		when(ocr.recognizeStayDocument(any())).thenReturn(JSON.createObjectNode(), pdfResult("masked", today));
		assertError(uploadPdf(), 422, "UNREADABLE_STAY_DOCUMENT");
		assertError(uploadPdf(), 422, "UNREADABLE_STAY_DOCUMENT");
		when(ocr.recognizeStayDocument(any())).thenThrow(RegistrationOcrClient.unreadableDocument());
		assertError(uploadPdf(), 422, "UNREADABLE_STAY_DOCUMENT");
		assertThat(jdbc.queryForObject("select document_verified_at from social_signup_registration", Object.class)).isNull();
		assertThat(jdbc.queryForObject("select document_expires_on from social_signup_registration", Object.class)).isNull();
		assertThat(decrypted().path("registrationNumber").asString()).isEqualTo(NUMBER);
		assertThat(decrypted().path("serialNumber").asString()).isEqualTo(SERIAL);
		assertThat(payload()).isEqualTo(before);
		doReturn(pdfResult(NUMBER, today.plusDays(1))).when(ocr).recognizeStayDocument(any());
		assertVerificationResponse(uploadPdf(), SERIAL, today.plusDays(1), true, false);
		assertThat(decrypted().has("serialNumber")).isFalse();
		verifyNoInteractions(apick);
	}

	@Test
	void pdfRequiresOnePdfPartWithNoExtraFieldsAndNoOversizedFile() throws Exception {
		preparePdf();
		assertError(send(uploadRequest("stay-document", PNG, "image/png", csrf, "", false)), 415, "UNSUPPORTED_FILE_TYPE");
		assertError(send(uploadRequest("stay-document", PNG, "application/pdf", csrf, "", false)), 415, "UNSUPPORTED_FILE_TYPE");
		assertError(send(uploadRequest("stay-document", new byte[0], "application/pdf", csrf, "", false)), 400, "INVALID_INPUT");
		assertError(send(uploadRequest("stay-document", new byte[5 * 1024 * 1024 + 1], "application/pdf", csrf, "", false)), 413, "FILE_TOO_LARGE");
		assertError(send(uploadRequest("stay-document", "%PDF-test".getBytes(StandardCharsets.UTF_8), "application/pdf", csrf, "", true)), 400, "INVALID_INPUT");
		assertError(send(uploadRequest("stay-document", "%PDF-test".getBytes(StandardCharsets.UTF_8), "application/pdf", csrf,
				"--boundary\r\nContent-Disposition: form-data; name=\"stayExpiresOn\"\r\n\r\n2099-01-01\r\n", false)), 400, "INVALID_INPUT");
		verify(ocr, never()).recognizeStayDocument(any());
		assertThat(decrypted().path("registrationNumber").asString()).isEqualTo(NUMBER);
	}

	@Test
	void pdfCannotCommitAfterExpiryAndConcurrentFrontInvalidatesItsResult() throws Exception {
		preparePdf();
		Instant expires = Instant.now().plusSeconds(2);
		jdbc.update("update social_auth_session set expires_at = ?", java.sql.Timestamp.from(expires));
		when(ocr.recognizeStayDocument(any())).thenAnswer(invocation -> {
			long wait = expires.toEpochMilli() - System.currentTimeMillis() + 100;
			if (wait > 0) { Thread.sleep(wait); }
			return pdfResult(NUMBER, today);
		});
		assertError(uploadPdf(), 401, "INVALID_SOCIAL_SESSION");
		assertThat(payload()).isNull();
		assertThat(jdbc.queryForObject("select document_verified_at from social_signup_registration", Object.class)).isNull();
		setSession(MemberRole.STUDENT);
		preparePdf();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(invocation -> {
			entered.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException(); }
			return pdfResult(NUMBER, today);
		}).when(ocr).recognizeStayDocument(any());
		var pdf = browser.sendAsync(uploadRequest("stay-document", "%PDF-test".getBytes(StandardCharsets.UTF_8), "application/pdf", csrf, "", false), HttpResponse.BodyHandlers.ofString());
		assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
		var front = browser.sendAsync(uploadRequest("front", PNG, "image/png", csrf, "", false), HttpResponse.BodyHandlers.ofString());
		release.countDown();
		assertThat(pdf.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(front.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select document_verified_at from social_signup_registration", Object.class)).isNull();
		assertThat(decrypted().path("registrationNumber").asString()).isEqualTo(NUMBER);
		assertThat(decrypted().has("serialNumber")).isFalse();
	}

	@Test
	void oldCardWithoutSerialReturnsExplicitNullAcrossPdfRetryAndCompletion() throws Exception {
		when(ocr.recognize(any(), anyString(), eq(true))).thenReturn(JSON.readTree(frontResult().toString().replace("2020-01-01", "2010-12-31")));
		agree();
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), null));
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertVerificationResponse(upload("back"), null, today.minusDays(1), false, true);
		assertThat(decrypted().has("serialNumber")).isTrue();
		assertThat(decrypted().path("serialNumber").isNull()).isTrue();
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult("0001016000000", today));
		assertError(uploadPdf(), 422, "STAY_DOCUMENT_MISMATCH");
		assertThat(decrypted().has("serialNumber")).isTrue();
		assertThat(decrypted().path("serialNumber").isNull()).isTrue();
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult(NUMBER, today.plusDays(30)));
		assertVerificationResponse(uploadPdf(), null, today.plusDays(30), true, false);
		assertThat(decrypted().has("serialNumber")).isFalse();
		verify(apick).verify(NUMBER, LocalDate.of(2010, 12, 31), null);
	}

	@Test
	void frontRecaptureReplacesPendingSerialAndPdfUsesOnlyTheNewCardSerial() throws Exception {
		preparePdf();
		assertThat(decrypted().path("serialNumber").asString()).isEqualTo(SERIAL);
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(decrypted().has("serialNumber")).isFalse();
		assertThat(jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class)).isNull();
		assertError(uploadPdf(), 409, "REGISTRATION_BACK_REQUIRED");
		String replacement = "00000000002";
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(2), replacement));
		assertVerificationResponse(upload("back"), replacement, today.minusDays(2), false, true);
		ObjectNode pdf = (ObjectNode) pdfResult(NUMBER, today.plusDays(10));
		pdf.put("serialNumber", SERIAL).put("permissionDate", "2020-01-01").put("reportDate", "2020-01-02");
		when(ocr.recognizeStayDocument(any())).thenReturn(pdf);
		assertVerificationResponse(uploadPdf(), replacement, today.plusDays(10), true, false);
		assertThat(decrypted().has("serialNumber")).isFalse();
	}

	@Test
	void failedFrontRecaptureDiscardsPendingCardSerialAndPdfState() throws Exception {
		preparePdf();
		when(ocr.recognize(any(), anyString(), eq(true))).thenThrow(RegistrationOcrClient.unreadable());
		assertError(upload("front"), 422, "UNREADABLE_REGISTRATION");
		assertThat(payload()).isNull();
		assertThat(jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class)).isNull();
		assertThat(jdbc.queryForObject("select document_verified_at from social_signup_registration", Object.class)).isNull();
		assertError(uploadPdf(), 409, "REGISTRATION_BACK_REQUIRED");
	}

	@Test
	void failedBackVerificationDoesNotPersistSerialOrReturnSuccess() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), SERIAL));
		doThrow(new SocialAuthException(HttpStatus.BAD_GATEWAY, "REGISTRATION_VERIFICATION_FAILED", "다시 시도해주세요."))
				.when(apick).verify(anyString(), any(), anyString());
		assertError(upload("back"), 502, "REGISTRATION_VERIFICATION_FAILED");
		assertThat(decrypted().has("serialNumber")).isFalse();
		assertThat(decrypted().path("registrationNumber").asString()).isEqualTo(NUMBER);
		assertThat(jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class)).isNull();
		doNothing().when(apick).verify(anyString(), any(), anyString());
		assertVerificationResponse(upload("back"), SERIAL, today.minusDays(1), false, true);
	}

	@Test
	void pdfUsesOnlyTheCurrentSignupSessionsCardSerial() throws Exception {
		preparePdf();
		String firstHash = jdbc.queryForObject("select token_hash from social_signup_registration", String.class);
		String firstPayload = payload();
		setSession(MemberRole.STUDENT, "other-pdf-registration");
		agree();
		assertError(uploadPdf(), 409, "REGISTRATION_BACK_REQUIRED");
		assertThat(upload("front").statusCode()).isEqualTo(200);
		String secondSerial = "00000000002";
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), secondSerial));
		assertThat(upload("back").statusCode()).isEqualTo(200);
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult(NUMBER, today));
		HttpResponse<String> response = uploadPdf();
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(JSON.readTree(response.body()).path("serialNumber").asString()).isEqualTo(secondSerial);
		assertThat(response.body()).doesNotContain(SERIAL);
		assertThat(jdbc.queryForObject("select front_payload from social_signup_registration where token_hash = ?", String.class, firstHash)).isEqualTo(firstPayload);
		String secondHash = jdbc.queryForObject("select token_hash from social_signup_registration where token_hash <> ?", String.class, firstHash);
		String secondPayload = jdbc.queryForObject("select front_payload from social_signup_registration where token_hash = ?", String.class, secondHash);
		assertThat(JSON.readTree(cipher.decrypt(secondPayload, secondHash)).has("serialNumber")).isFalse();
	}

	@Test
	void legacyPendingPdfSessionRequiresRecaptureBeforeOcr() throws Exception {
		preparePdf();
		String hash = jdbc.queryForObject("select token_hash from social_signup_registration", String.class);
		ObjectNode legacy = (ObjectNode) decrypted();
		legacy.remove("serialNumber");
		String encrypted = cipher.encrypt(legacy.toString(), hash);
		jdbc.update("update social_signup_registration set front_payload = ? where token_hash = ?", encrypted, hash);
		assertError(uploadPdf(), 409, "REGISTRATION_FRONT_REQUIRED");
		assertThat(payload()).isEqualTo(encrypted);
		verifyNoInteractions(ocr, apick);
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertVerificationResponse(upload("back"), SERIAL, today.minusDays(1), false, true);
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult(NUMBER, today));
		assertVerificationResponse(uploadPdf(), SERIAL, today, true, false);
	}

	@Test
	void expiredPendingPdfSessionCannotReturnSerialAndCleanupDeletesCiphertext() throws Exception {
		preparePdf();
		assertThat(decrypted().path("serialNumber").asString()).isEqualTo(SERIAL);
		jdbc.update("update social_auth_session set expires_at = now() - interval '1 second'");
		assertError(uploadPdf(), 401, "INVALID_SOCIAL_SESSION");
		verifyNoInteractions(ocr, apick);
		auth.deleteExpiredSessions();
		assertThat(count("social_signup_registration")).isZero();
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void verificationSerialIsRedactedFromErrorsAndLogs(CapturedOutput output) throws Exception {
		preparePdf();
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult("0001016000000", today));
		assertError(uploadPdf(), 422, "STAY_DOCUMENT_MISMATCH");
		RegistrationVerificationResponse response = new RegistrationVerificationResponse(SERIAL, today, true, false, true, Instant.now());
		assertThat(response.toString()).isEqualTo("RegistrationVerificationResponse[redacted]");
		assertThat(output.getAll()).doesNotContain(NUMBER, DISPLAY_NUMBER, SERIAL);
	}

	@Test
	void pdfOpenApiDescribesOneBinaryPartAndSpecificFailures() throws Exception {
		JsonNode document = JSON.readTree(get("/v3/api-docs").body());
		JsonNode operation = document.path("paths").path(PATH + "stay-document").path("post");
		assertThat(operation.path("security").isEmpty()).isTrue();
		JsonNode schema = resolve(document, operation.path("requestBody").path("content").path("multipart/form-data").path("schema"));
		assertThat(schema.path("properties").propertyNames()).containsExactly("stayDocument");
		assertThat(schema.path("properties").path("stayDocument").path("format").asString()).isEqualTo("binary");
		assertThat(operation.path("responses").propertyNames()).contains("200", "400", "401", "403", "409", "413", "415", "422", "502", "503", "500");
		assertThat(operation.path("responses").path("422").toString()).contains("STAY_DOCUMENT_MISMATCH", "STAY_DOCUMENT_EXPIRED", "UNREADABLE_STAY_DOCUMENT");
		assertThat(operation.path("parameters").toString()).contains("socialSignup", "X-XSRF-TOKEN", "XSRF-TOKEN");
	}

	@Test
	void bothVerificationApisExposeTheSameSchemaAndStateExamples() throws Exception {
		JsonNode document = JSON.readTree(get("/v3/api-docs").body());
		JsonNode back = document.path("paths").path(PATH + "back").path("post").path("responses").path("200")
				.path("content").path("application/json");
		JsonNode pdf = document.path("paths").path(PATH + "stay-document").path("post").path("responses").path("200")
				.path("content").path("application/json");
		assertThat(back.path("schema")).isEqualTo(pdf.path("schema"));
		assertThat(back.path("schema").path("$ref").asString()).endsWith("/RegistrationVerificationResponse");
		JsonNode schema = resolve(document, back.path("schema"));
		assertThat(schema.path("properties").propertyNames()).containsExactlyInAnyOrder(
				"serialNumber", "stayExpiresOn", "cardVerified", "stayDocumentVerified", "requiresStayDocument", "signupExpiresAt");
		assertThat(schema.path("required").size()).isEqualTo(6);
		JsonNode serial = schema.path("properties").path("serialNumber");
		assertThat(serial.path("type").toString()).contains("string", "null");
		assertThat(serial.path("minLength").asInt()).isEqualTo(11);
		assertThat(serial.path("maxLength").asInt()).isEqualTo(11);
		assertThat(serial.path("example").asString()).isEqualTo(SERIAL);
		assertThat(SERIAL.matches(serial.path("pattern").asString())).isTrue();
		for (String name : List.of("cardValid", "requiresPdf", "pdfVerified")) {
			JsonNode example = (name.equals("pdfVerified") ? pdf : back).path("examples").path(name).path("value");
			if (example.isString()) { example = JSON.readTree(example.asString()); }
			assertThat(example.propertyNames()).containsExactlyInAnyOrder(schema.path("properties").propertyNames().toArray(String[]::new));
			assertThat(example.path("serialNumber").asString()).isEqualTo(SERIAL);
			assertThat(example.path("cardVerified").asBoolean()).isTrue();
			assertThat(example.path("stayDocumentVerified").asBoolean()).isEqualTo(name.equals("pdfVerified"));
			assertThat(example.path("requiresStayDocument").asBoolean()).isEqualTo(name.equals("requiresPdf"));
		}
		assertThat(document.path("components").path("schemas").has("RegistrationBackResponse")).isFalse();
		assertThat(document.path("components").path("schemas").has("RegistrationStayDocumentResponse")).isFalse();
	}

	@Test
	void birthDateBlocksPhoneBeforeAgeCheckAndAcceptsEighteenthBirthdayWithoutExtendingSession() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		Object expires = jdbc.queryForObject("select expires_at from social_auth_session", Object.class);
		assertError(phoneRequest(), 409, "BIRTH_DATE_REQUIRED");
		assertError(phoneConfirm("123456"), 409, "BIRTH_DATE_REQUIRED");
		assertError(saveBirthDate(today.minusYears(18).plusDays(1)), 422, "AGE_REQUIREMENT_NOT_MET");
		assertError(saveBirthDate(today.plusDays(1)), 422, "AGE_REQUIREMENT_NOT_MET");
		assertThat(jdbc.queryForObject("select birth_date from social_auth_session", LocalDate.class)).isNull();
		assertThat(users.count()).isZero();
		assertThat(count("phone_verification_send_attempt")).isZero();
		verifyNoInteractions(smsSender);
		HttpResponse<String> saved = saveBirthDate(today.minusYears(18));
		assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
		assertThat(saved.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(JSON.readTree(saved.body()).path("ageVerified").asBoolean()).isTrue();
		assertThat(jdbc.queryForObject("select expires_at from social_auth_session", Object.class)).isEqualTo(expires);
		assertThat(phoneConfirm(sendPhoneCode()).statusCode()).isEqualTo(201);
		assertThat(jdbc.queryForObject("select birth_date from student_registration_verification", LocalDate.class)).isEqualTo(today.minusYears(18));
		assertThat(count("social_auth_session")).isZero();
	}

	@Test
	void birthDateRequiresConsentRegistrationPdfSessionAndCsrf() throws Exception {
		assertError(saveBirthDate(today.minusYears(20)), 400, "REQUIRED_TERMS_NOT_AGREED");
		agree();
		assertError(saveBirthDate(today.minusYears(20)), 409, "REGISTRATION_VERIFICATION_REQUIRED");
		preparePdf();
		assertError(saveBirthDate(today.minusYears(20)), 409, "STAY_DOCUMENT_REQUIRED");
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult(NUMBER, today));
		assertThat(uploadPdf().statusCode()).isEqualTo(200);
		String body = "{\"birthDate\":\"2000-05-14\"}";
		HttpRequest noCsrf = HttpRequest.newBuilder(uri("/api/auth/social/birth-date"))
				.header("Content-Type", "application/json").header("Authorization", "Bearer ignored")
				.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
		assertError(send(noCsrf), 403, "INVALID_CSRF_TOKEN");
		assertThat(saveBirthDate(today.minusYears(20)).statusCode()).isEqualTo(200);
		jdbc.update("update social_auth_session set expires_at = now() - interval '1 second'");
		assertError(saveBirthDate(today.minusYears(20)), 401, "INVALID_SOCIAL_SESSION");
		setSession(MemberRole.EMPLOYER);
		assertError(saveBirthDate(today.minusYears(20)), 403, "FORBIDDEN");
		cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("socialSignup"))
				.toList().forEach(c -> cookies.getCookieStore().remove(uri("/"), c));
		assertError(saveBirthDate(today.minusYears(20)), 401, "INVALID_SOCIAL_SESSION");
		assertThat(users.count()).isZero();
		verifyNoInteractions(smsSender);
	}

	@Test
	void birthDateRejectsMalformedInputAndDoesNotOverwriteAcceptedDate() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		assertThat(saveBirthDate(LocalDate.of(2000, 5, 14)).statusCode()).isEqualTo(200);
		for (String body : List.of("{}", "{\"birthDate\":null}", "{\"birthDate\":20000514}",
				"{\"birthDate\":\"2000-02-30\"}", "{\"birthDate\":\"2000-5-14\"}",
				"{\"birthDate\":\"2000-05-14\",\"userId\":1}")) {
			assertError(send(birthDateRequest(body)), 400, "INVALID_INPUT");
		}
		assertError(saveBirthDate(today.minusYears(10)), 422, "AGE_REQUIREMENT_NOT_MET");
		assertThat(jdbc.queryForObject("select birth_date from social_auth_session", LocalDate.class)).isEqualTo(LocalDate.of(2000, 5, 14));
		assertThat(saveBirthDate(LocalDate.of(1999, 2, 3)).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select birth_date from social_auth_session", LocalDate.class)).isEqualTo(LocalDate.of(1999, 2, 3));
		assertThat(users.count()).isZero();
	}

	@Test
	void phoneConfirmationRechecksStoredBirthDateBeforeCreatingAccount() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		jdbc.update("update social_auth_session set birth_date = null");
		assertError(phoneConfirm(code), 409, "BIRTH_DATE_REQUIRED");
		jdbc.update("update social_auth_session set birth_date = ?", today.minusYears(10));
		assertError(phoneRequest(), 422, "AGE_REQUIREMENT_NOT_MET");
		assertError(phoneConfirm(code), 422, "AGE_REQUIREMENT_NOT_MET");
		assertThat(users.count()).isZero();
		assertThat(count("student_registration_verification")).isZero();
		assertThat(saveBirthDate(LocalDate.of(2000, 5, 14)).statusCode()).isEqualTo(200);
		assertThat(phoneConfirm(code).statusCode()).isEqualTo(201);
	}

	@Test
	void birthDateOpenApiDocumentsCookieSecurityAndAgeErrors() throws Exception {
		JsonNode document = JSON.readTree(get("/v3/api-docs").body());
		JsonNode operation = document.path("paths").path("/api/auth/social/birth-date").path("put");
		assertThat(operation.path("parameters").toString()).contains("socialSignup", "X-XSRF-TOKEN", "XSRF-TOKEN");
		assertThat(operation.path("responses").path("422").toString()).contains("AGE_REQUIREMENT_NOT_MET");
		assertThat(document.path("components").path("schemas").path("SocialSignupBirthDateRequest").path("required").toString()).contains("birthDate");
	}

	@Test
	void phoneSignupTransfersCardResultAndOriginalConsentWithoutCreatingProfile() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		Object cardAt = jdbc.queryForObject("select card_verified_at from social_signup_registration", Object.class);
		var consent = jdbc.queryForList("select term_type_id, is_agreed, agreed_at from social_auth_session_term_agreement order by term_type_id");
		String code = sendPhoneCode();
		assertThat(users.count()).isZero();
		HttpResponse<String> response = phoneConfirm(code);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		assertThat(JSON.readTree(response.body()).path("onboardingRequired").asBoolean()).isTrue();
		assertThat(response.headers().allValues("Set-Cookie")).anyMatch(value -> value.startsWith("socialSignup=") && value.contains("Max-Age=0"));
		assertThat(jdbc.queryForObject("select name from user_account", String.class)).isEqualTo("TEST STUDENT");
		assertThat(jdbc.queryForObject("select nationality from student_registration_verification", String.class)).isEqualTo("VN");
		assertThat(jdbc.queryForObject("select visa_code from student_registration_verification", String.class)).isEqualTo("D-2");
		assertThat(jdbc.queryForObject("select birth_date from student_registration_verification", LocalDate.class)).isEqualTo(LocalDate.of(2000, 5, 14));
		String accessToken = JSON.readTree(response.body()).path("accessToken").asString();
		HttpResponse<String> status = send(HttpRequest.newBuilder(uri("/api/users/me/onboarding"))
				.header("Authorization", "Bearer " + accessToken).GET().build());
		assertThat(status.statusCode()).isEqualTo(200);
		assertThat(JSON.readTree(status.body()).path("nextStep").asString()).isEqualTo("CURRENT_REGION");
		assertThat(jdbc.queryForObject("select stay_expires_on from student_registration_verification", LocalDate.class)).isEqualTo(today);
		assertThat(jdbc.queryForObject("select card_verified_at from student_registration_verification", Object.class)).isEqualTo(cardAt);
		assertThat(jdbc.queryForObject("select document_verified_at from student_registration_verification", Object.class)).isNull();
		assertThat(jdbc.queryForList("select term_type_id, is_agreed, agreed_at from user_term_agreement order by term_type_id")).isEqualTo(consent);
		for (String table : List.of("student_profile", "social_auth_session", "social_signup_registration", "phone_verification")) {
			assertThat(count(table)).as(table).isZero();
		}
		assertThat(count("auth_account")).isEqualTo(1);
		assertThat(count("refresh_token")).isEqualTo(1);
	}

	@Test
	void phoneSignupTransfersPdfExpiryAndVerificationTime() throws Exception {
		preparePdf();
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult(NUMBER, today.plusDays(30)));
		assertThat(uploadPdf().statusCode()).isEqualTo(200);
		Object documentAt = jdbc.queryForObject("select document_verified_at from social_signup_registration", Object.class);
		String code = sendPhoneCode();
		assertThat(phoneConfirm(code).statusCode()).isEqualTo(201);
		assertThat(jdbc.queryForObject("select stay_expires_on from student_registration_verification", LocalDate.class)).isEqualTo(today.plusDays(30));
		assertThat(jdbc.queryForObject("select document_verified_at from student_registration_verification", Object.class)).isEqualTo(documentAt);
		assertThat(count("social_signup_registration")).isZero();
		assertThat(jdbc.queryForMap("select * from student_registration_verification").toString()).doesNotContain(NUMBER, DISPLAY_NUMBER, SERIAL, "serial_number", "front_payload");
		assertThat(jdbc.queryForMap("select * from user_account").toString()).doesNotContain(NUMBER, DISPLAY_NUMBER, SERIAL, "serial_number", "front_payload");
		verifyNoInteractions(apick);
	}

	@Test
	void bothPhoneEndpointsBlockMissingCardAndPendingPdfBeforeSmsOrAccountCreation() throws Exception {
		agree();
		assertError(phoneRequest(), 409, "REGISTRATION_VERIFICATION_REQUIRED");
		assertError(phoneConfirm("123456"), 409, "REGISTRATION_VERIFICATION_REQUIRED");
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertError(phoneRequest(), 409, "REGISTRATION_VERIFICATION_REQUIRED");
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), "00000000001"));
		assertThat(upload("back").statusCode()).isEqualTo(200);
		assertError(phoneRequest(), 409, "STAY_DOCUMENT_REQUIRED");
		assertError(phoneConfirm("123456"), 409, "STAY_DOCUMENT_REQUIRED");
		assertThat(count("phone_verification_send_attempt")).isZero();
		assertThat(count("phone_verification")).isZero();
		assertThat(users.count()).isZero();
		verifyNoInteractions(smsSender);
	}

	@Test
	void phoneConfirmationRechecksRecaptureAndStayValidityAfterCodeWasSent() throws Exception {
		preparePdf();
		when(ocr.recognizeStayDocument(any())).thenReturn(pdfResult(NUMBER, today));
		assertThat(uploadPdf().statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		jdbc.update("update social_signup_registration set document_expires_on = ?", today.minusDays(1));
		assertError(phoneConfirm(code), 422, "STAY_DOCUMENT_EXPIRED");
		jdbc.update("update social_signup_registration set document_expires_on = ?", today);
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertError(phoneConfirm(code), 409, "REGISTRATION_VERIFICATION_REQUIRED");
		assertThat(users.count()).isZero();
		assertThat(count("student_registration_verification")).isZero();
		assertThat(count("phone_verification")).isEqualTo(1);
	}

	@Test
	void cardExpiringAfterSmsRequiresRecaptureWhenNumberWasAlreadyDeleted() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		jdbc.update("update social_signup_registration set stay_expires_on = ?", today.minusDays(1));
		assertError(phoneConfirm(code), 409, "REGISTRATION_VERIFICATION_REQUIRED");
		assertThat(users.count()).isZero();
	}

	@Test
	void duplicatePhoneDeletesTemporaryRegistrationAndPreservesExistingUser() throws Exception {
		UserAccount existing = new UserAccount("existing@example.com", MemberRole.EMPLOYER);
		existing.verifyPhoneNumber("01000000000", Instant.now());
		users.saveAndFlush(existing);
		var before = jdbc.queryForList("select * from user_account");
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		HttpResponse<String> response = phoneConfirm(code);
		assertError(response, 409, "DUPLICATE_PHONE_NUMBER");
		assertThat(response.headers().allValues("Set-Cookie")).hasSize(1);
		assertThat(jdbc.queryForList("select * from user_account")).isEqualTo(before);
		assertThat(count("social_signup_registration")).isZero();
		assertThat(count("student_registration_verification")).isZero();
		assertThat(count("social_auth_session")).isZero();
	}

	@Test
	void transferFailureRollsBackUserIdentityTokensAndLeavesRetryableSession() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		doThrow(new IllegalStateException("Storage unavailable")).when(verifiedRegistrations).saveAndFlush(any());
		HttpResponse<String> response = phoneConfirm(code);
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		for (String table : List.of("user_account", "auth_account", "user_term_agreement", "refresh_token", "student_registration_verification")) {
			assertThat(count(table)).as(table).isZero();
		}
		assertThat(count("social_auth_session")).isEqualTo(1);
		assertThat(count("social_signup_registration")).isEqualTo(1);
		assertThat(count("phone_verification")).isEqualTo(1);
	}

	@Test
	void tokenFailureRollsBackAlreadyPersistedVerificationAndConsent() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		doThrow(new IllegalStateException("Token unavailable")).when(tokenService).issueTokens(any());
		assertError(phoneConfirm(code), 500, "INTERNAL_SERVER_ERROR");
		verify(verifiedRegistrations).saveAndFlush(any());
		for (String table : List.of("user_account", "auth_account", "user_term_agreement", "refresh_token", "student_registration_verification")) {
			assertThat(count(table)).as(table).isZero();
		}
		assertThat(count("social_auth_session")).isEqualTo(1);
		assertThat(count("social_signup_registration")).isEqualTo(1);
		assertThat(count("phone_verification")).isEqualTo(1);
	}

	@Test
	void phoneOpenApiIncludesRegistrationErrorsAndPreservesSuccessContracts() throws Exception {
		JsonNode document = JSON.readTree(get("/v3/api-docs").body());
		for (String suffix : List.of("", "/verify")) {
			JsonNode operation = document.path("paths").path("/api/auth/social/phone-verifications" + suffix).path("post");
			assertThat(operation.path("responses").path("409").toString()).contains("REGISTRATION_VERIFICATION_REQUIRED", "STAY_DOCUMENT_REQUIRED");
			assertThat(operation.path("responses").path("422").toString()).contains("STAY_DOCUMENT_EXPIRED");
			assertThat(operation.path("responses").path("503").toString()).contains("REGISTRATION_VERIFICATION_UNAVAILABLE", "SOCIAL_LOGIN_UNAVAILABLE");
			assertThat(operation.path("responses").has(suffix.isEmpty() ? "200" : "201")).isTrue();
		}
	}

	@Test
	void phoneSignupStillRequiresIdentityConsentAfterSms() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		jdbc.update("delete from social_auth_session_term_agreement where term_type_id in (select id from term_type where is_student_registration)");
		assertError(phoneConfirm(code), 400, "REQUIRED_TERMS_NOT_AGREED");
		assertThat(users.count()).isZero();
	}

	@Test
	void phoneLockWaitCannotSendSmsAfterSessionExpiry() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		assertThat(saveBirthDate(LocalDate.of(2000, 5, 14)).statusCode()).isEqualTo(200);
		Instant expires = Instant.now().plusSeconds(2);
		jdbc.update("update social_auth_session set expires_at = ?", java.sql.Timestamp.from(expires));
		delayPhoneLockUntil(expires);
		assertError(phoneRequest(), 401, "INVALID_SOCIAL_SESSION");
		verifyNoInteractions(smsSender);
		assertThat(count("phone_verification_send_attempt")).isZero();
	}

	@Test
	void phoneLockWaitRechecksCodeAndSessionExpiryBeforeAccountCreation() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		Instant expires = Instant.now().plusSeconds(2);
		jdbc.update("update phone_verification set expires_at = ?", java.sql.Timestamp.from(expires));
		delayPhoneLockUntil(expires);
		assertError(phoneConfirm(code), 400, "INVALID_VERIFICATION_CODE");
		Instant sessionExpiry = Instant.now().plusSeconds(2);
		jdbc.update("update phone_verification set expires_at = now() + interval '300 seconds'");
		jdbc.update("update social_auth_session set expires_at = ?", java.sql.Timestamp.from(sessionExpiry));
		delayPhoneLockUntil(sessionExpiry);
		assertError(phoneConfirm(code), 401, "INVALID_SOCIAL_SESSION");
		assertThat(users.count()).isZero();
		assertThat(count("student_registration_verification")).isZero();
	}

	@Test
	void employerDoesNotRequireRegistrationWhenFeatureIsEnabled() throws Exception {
		setSession(MemberRole.EMPLOYER);
		List<Map<String, Object>> values = jdbc.queryForList("select id from term_type where is_social_signup and is_required and not is_student_registration", Long.class)
				.stream().map(id -> Map.<String, Object>of("termTypeId", id, "isAgreed", true)).toList();
		assertThat(saveTerms(values).statusCode()).isEqualTo(200);
		assertThat(phoneConfirm(sendPhoneCode()).statusCode()).isEqualTo(201);
		assertThat(jdbc.queryForObject("select role from user_account", String.class)).isEqualTo("EMPLOYER");
		assertThat(count("student_registration_verification")).isZero();
		verifyNoInteractions(ocr, apick);
	}

	@Test
	void parallelPhoneConfirmationsCreateOnlyOneVerifiedRegistration() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		HttpRequest request = phoneHttpRequest("/verify", Map.of("phoneNumber", "01000000000", "code", code));
		var first = browser.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		var second = browser.sendAsync(request, HttpResponse.BodyHandlers.ofString());
		List<Integer> statuses = List.of(first.get(15, TimeUnit.SECONDS).statusCode(), second.get(15, TimeUnit.SECONDS).statusCode());
		assertThat(statuses).containsOnlyOnce(201).allMatch(status -> status == 201 || status == 401 || status == 409);
		assertThat(users.count()).isEqualTo(1);
		assertThat(count("student_registration_verification")).isEqualTo(1);
	}

	private void delayPhoneLockUntil(Instant expires) {
		doAnswer(invocation -> {
			long wait = expires.toEpochMilli() - System.currentTimeMillis() + 100;
			if (wait > 0) { Thread.sleep(wait); }
			return true;
		}).when(attempts).lockPhoneNumber(anyString());
	}
	private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
	private HttpRequest phoneHttpRequest(String suffix, Map<String, String> value) {
		return HttpRequest.newBuilder(uri("/api/auth/social/phone-verifications" + suffix))
				.header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf)
				.header("Authorization", "Bearer ignored-for-cookie-auth")
				.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(value))).build();
	}
	private HttpResponse<String> phoneRequest() throws Exception {
		return send(phoneHttpRequest("", Map.of("phoneNumber", "01000000000")));
	}
	private HttpResponse<String> phoneConfirm(String code) throws Exception {
		return send(phoneHttpRequest("/verify", Map.of("phoneNumber", "01000000000", "code", code)));
	}
	private String sendPhoneCode() throws Exception {
		if ("STUDENT".equals(jdbc.queryForObject("select role from social_auth_session", String.class))
				&& jdbc.queryForObject("select birth_date from social_auth_session", LocalDate.class) == null) {
			assertThat(saveBirthDate(LocalDate.of(2000, 5, 14)).statusCode()).isEqualTo(200);
		}
		HttpResponse<String> response = phoneRequest();
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		ArgumentCaptor<SmsSendRequest> sent = ArgumentCaptor.forClass(SmsSendRequest.class);
		verify(smsSender, atLeastOnce()).sendAndWait(sent.capture());
		String message = sent.getValue().message();
		return message.substring("[HIRELINK] 인증번호 [".length(), message.length() - 1);
	}
	private HttpRequest birthDateRequest(String body) {
		return HttpRequest.newBuilder(uri("/api/auth/social/birth-date"))
				.header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf)
				.header("Authorization", "Bearer ignored-for-cookie-auth")
				.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
	}
	private HttpResponse<String> saveBirthDate(LocalDate date) throws Exception {
		return send(birthDateRequest("{\"birthDate\":\"" + date + "\"}"));
	}

	private void preparePdf() throws Exception {
		agree();
		when(ocr.recognize(any(), anyString(), eq(false))).thenReturn(backResult(today.minusDays(1), "00000000001"));
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		clearInvocations(ocr, apick);
	}
	private JsonNode pdfResult(String number, LocalDate expiry) {
		return JSON.createObjectNode().put("registrationNumber", number).put("stayExpiresOn", expiry.toString());
	}
	private HttpResponse<String> uploadPdf() throws Exception {
		return send(uploadRequest("stay-document", "%PDF-test".getBytes(StandardCharsets.UTF_8), "application/pdf", csrf, "", false));
	}

	private JsonNode resolve(JsonNode document, JsonNode schema) {
		return schema.has("$ref") ? document.at(schema.path("$ref").asString().substring(1)) : schema;
	}
	@Test
	void retainsD2RegistrationCodeUntilOnboardingProgramSelection() throws Exception {
		agree();
		HttpResponse<String> front = upload("front");
		assertThat(front.statusCode()).isEqualTo(200);
		assertThat(JSON.readTree(front.body()).path("visaCode").asString()).isEqualTo("D-2");
		assertThat(upload("back").statusCode()).isEqualTo(200);
	}

	@ParameterizedTest
	@ValueSource(strings = {"D-4", "D-4-1", "D-4-7", "D-2-7", "D-2-8"})
	void unsupportedRegistrationInvalidatesPreviousResultAndCannotReachVerification(String visa) throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		when(ocr.recognize(any(), anyString(), eq(true))).thenReturn(((ObjectNode) frontResult()).put("visaCode", visa));
		assertError(upload("front"), 422, "UNSUPPORTED_VISA");
		assertThat(payload()).isNull();
		assertError(upload("back"), 409, "REGISTRATION_FRONT_REQUIRED");
		assertError(phoneRequest(), 409, "REGISTRATION_VERIFICATION_REQUIRED");
		verifyNoInteractions(apick, smsSender);
		assertThat(users.count()).isZero();
	}

	@Test
	void oldUnsupportedVerifiedSessionCannotCreateAccountEvenWithIssuedPhoneCode() throws Exception {
		agree();
		assertThat(upload("front").statusCode()).isEqualTo(200);
		assertThat(upload("back").statusCode()).isEqualTo(200);
		String code = sendPhoneCode();
		String hash = jdbc.queryForObject("select token_hash from social_signup_registration", String.class);
		ObjectNode legacy = (ObjectNode) decrypted();
		legacy.put("visaCode", "D-4");
		jdbc.update("update social_signup_registration set front_payload = ? where token_hash = ?", cipher.encrypt(legacy.toString(), hash), hash);
		clearInvocations(smsSender);
		assertError(phoneRequest(), 422, "UNSUPPORTED_VISA");
		assertError(phoneConfirm(code), 422, "UNSUPPORTED_VISA");
		assertThat(users.count()).isZero();
		assertThat(count("auth_account")).isZero();
		assertThat(count("refresh_token")).isZero();
		verifyNoInteractions(smsSender);
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
		setSession(role, "registration-test");
	}
	private void setSession(MemberRole role, String subject) {
		token = auth.createSession(AuthProvider.GOOGLE, subject, role, null);
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
		String part = side.equals("stay-document") ? "stayDocument" : "registrationFile";
		String header = "--boundary\r\nContent-Disposition: form-data; name=\"" + part + "\"; filename=\"capture\"\r\nContent-Type: " + kind + "\r\n\r\n";
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
	private void assertVerificationResponse(HttpResponse<String> response, String serial, LocalDate expiry,
			boolean documentVerified, boolean requiresDocument) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
		JsonNode body = JSON.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder(
				"serialNumber", "stayExpiresOn", "cardVerified", "stayDocumentVerified", "requiresStayDocument", "signupExpiresAt");
		if (serial == null) {
			assertThat(body.path("serialNumber").isNull()).isTrue();
		} else {
			assertThat(body.path("serialNumber").asString()).isEqualTo(serial);
		}
		assertThat(body.path("stayExpiresOn").asString()).isEqualTo(expiry.toString());
		assertThat(body.path("cardVerified").asBoolean()).isTrue();
		assertThat(body.path("stayDocumentVerified").asBoolean()).isEqualTo(documentVerified);
		assertThat(body.path("requiresStayDocument").asBoolean()).isEqualTo(requiresDocument);
		assertThat(Instant.parse(body.path("signupExpiresAt").asString())).isEqualTo(jdbc.queryForObject(
				"select expires_at from social_auth_session", java.sql.Timestamp.class).toInstant());
	}
	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo(code);
		assertThat(response.body()).doesNotContain(NUMBER, DISPLAY_NUMBER, SERIAL);
	}
}
