package com.hirelink.domain.auth.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hirelink.domain.auth.client.ApickRegistrationClient;
import com.hirelink.domain.auth.client.RegistrationOcrClient;
import com.hirelink.domain.auth.dto.RegistrationBackResponse;
import com.hirelink.domain.auth.dto.RegistrationFrontResponse;
import com.hirelink.domain.auth.entity.SocialAuthSession;
import com.hirelink.domain.auth.entity.SocialSignupRegistration;
import com.hirelink.domain.auth.repository.SocialSignupRegistrationRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.TermType;
import com.hirelink.global.exception.SocialAuthException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SocialSignupRegistrationService {

	private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
	private static final Set<String> VISAS = Set.of("D-2", "D-4", "D-2-1", "D-2-2", "D-2-3", "D-2-4", "D-2-6", "D-2-7", "D-2-8", "D-4-1", "D-4-7");
	private final SocialAuthService auth;
	private final SocialSignupTermsService terms;
	private final SocialSignupRegistrationRepository registrations;
	private final RegistrationCipher cipher;
	private final RegistrationOcrClient ocr;
	private final ApickRegistrationClient apick;
	private final ObjectMapper mapper;
	private final boolean enabled;
	private final boolean socialEnabled;

	public SocialSignupRegistrationService(SocialAuthService auth, SocialSignupTermsService terms,
			SocialSignupRegistrationRepository registrations, RegistrationCipher cipher, RegistrationOcrClient ocr,
			ApickRegistrationClient apick, ObjectMapper mapper,
			@Value("${registration-verification.enabled:false}") boolean enabled,
			@Value("${auth.social.enabled:false}") boolean socialEnabled) {
		this.auth = auth;
		this.terms = terms;
		this.registrations = registrations;
		this.cipher = cipher;
		this.ocr = ocr;
		this.apick = apick;
		this.mapper = mapper;
		this.enabled = enabled;
		this.socialEnabled = socialEnabled;
	}

	// 외부 처리 실패도 재촬영 전 결과 무효화를 커밋한다.
	@Transactional(noRollbackFor = SocialAuthException.class)
	public RegistrationFrontResponse front(String token, MultipartFile file) {
		SocialAuthSession session = requireSession(token);
		byte[] bytes = readImage(file);
		try {
			SocialSignupRegistration registration = registrations.findById(session.getTokenHash())
					.orElseGet(() -> new SocialSignupRegistration(session.getTokenHash()));
			registration.resetFront();
			registrations.saveAndFlush(registration);
			JsonNode result = ocr.recognize(bytes, file.getContentType(), true);
			String name = text(result, "name");
			String country = countryCode(text(result, "nationality"));
			String visa = text(result, "visaCode");
			String number = text(result, "registrationNumber");
			LocalDate issued = date(result, "issueDate");
			if (name.length() > 100 || !VISAS.contains(visa) || !number.matches("[0-9]{6}[5-8][0-9]{6}")
					|| issued.isAfter(LocalDate.now(KOREA))) { throw RegistrationOcrClient.unreadable(); }
			requireUnexpired(session, registration);
			ObjectNode payload = mapper.createObjectNode();
			payload.put("name", name).put("nationalityCode", country).put("visaCode", visa)
					.put("registrationNumber", number).put("issueDate", issued.toString());
			registration.saveFront(cipher.encrypt(mapper.writeValueAsString(payload), session.getTokenHash()));
			return new RegistrationFrontResponse(name, country, visa, number.substring(0, 6) + "-*******", issued, session.getExpiresAt());
		} finally {
			Arrays.fill(bytes, (byte) 0);
		}
	}

	@Transactional(noRollbackFor = SocialAuthException.class)
	public RegistrationBackResponse back(String token, MultipartFile file) {
		SocialAuthSession session = requireSession(token);
		SocialSignupRegistration registration = registrations.findById(session.getTokenHash()).orElseThrow(this::frontRequired);
		if (registration.getFrontPayload() == null) { throw frontRequired(); }
		JsonNode front = mapper.readTree(cipher.decrypt(registration.getFrontPayload(), session.getTokenHash()));
		if (!front.path("registrationNumber").isString()) { throw frontRequired(); }
		byte[] bytes = readImage(file);
		try {
			registration.resetBack();
			registrations.flush();
			JsonNode result = ocr.recognize(bytes, file.getContentType(), false);
			LocalDate expiry = date(result, "stayExpiresOn");
			LocalDate issued = date(front, "issueDate");
			JsonNode serial = result.path("serialNumber");
			String serialNumber = serial.isString() ? serial.stringValue() : null;
			if ((serialNumber != null && !serialNumber.matches("[0-9]{11}"))
					|| (!issued.isBefore(LocalDate.of(2011, 1, 1)) && serialNumber == null)) {
				throw RegistrationOcrClient.unreadable();
			}
			requireUnexpired(session, registration);
			apick.verify(text(front, "registrationNumber"), issued, serialNumber);
			requireUnexpired(session, registration);
			boolean requiresPdf = expiry.isBefore(LocalDate.now(KOREA));
			// PDF의 동일인 확인이 필요한 경우에만 세션 만료까지 번호를 유지한다.
			if (!requiresPdf) { ((ObjectNode) front).remove("registrationNumber"); }
			registration.verify(cipher.encrypt(mapper.writeValueAsString(front), session.getTokenHash()), expiry, Instant.now());
			return new RegistrationBackResponse(true, expiry, requiresPdf, session.getExpiresAt());
		} finally {
			Arrays.fill(bytes, (byte) 0);
		}
	}

	private SocialAuthSession requireSession(String token) {
		if (!enabled || !socialEnabled) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRATION_VERIFICATION_UNAVAILABLE", "등록증 확인 서비스를 이용할 수 없습니다.");
		}
		SocialAuthSession session = auth.requireSignupSession(token);
		if (session.getRole() != MemberRole.STUDENT) {
			throw new SocialAuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "유학생 가입 중에만 등록증을 확인할 수 있습니다.");
		}
		boolean identityTermFound = false;
		for (TermType term : terms.signupTerms(session)) {
			identityTermFound |= term.isStudentRegistration();
			if (term.isRequired() && session.getAgreements().stream().noneMatch(agreement ->
					agreement.getTermTypeId().equals(term.getId()) && agreement.isAgreed() && agreement.getAgreedAt() != null)) {
				throw new SocialAuthException(HttpStatus.BAD_REQUEST, "REQUIRED_TERMS_NOT_AGREED", "고유식별정보 동의를 포함한 필수 약관에 모두 동의해주세요.");
			}
		}
		if (!identityTermFound) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRATION_VERIFICATION_UNAVAILABLE", "등록증 확인 서비스를 이용할 수 없습니다.");
		}
		cipher.requireConfigured();
		return session;
	}

	private void requireUnexpired(SocialAuthSession session, SocialSignupRegistration registration) {
		if (!session.getExpiresAt().isAfter(Instant.now())) {
			registration.resetFront();
			throw new SocialAuthException(HttpStatus.UNAUTHORIZED, "INVALID_SOCIAL_SESSION", "소셜 인증을 다시 진행해주세요.");
		}
	}

	private byte[] readImage(MultipartFile file) {
		if (file.isEmpty()) { throw new SocialAuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "등록증 촬영 이미지를 첨부해주세요."); }
		if (file.getSize() > MAX_FILE_SIZE) { throw tooLarge(); }
		byte[] bytes = null;
		try (InputStream input = file.getInputStream()) {
			bytes = input.readNBytes(MAX_FILE_SIZE + 1);
			if (bytes.length > MAX_FILE_SIZE) { throw tooLarge(); }
			boolean png = "image/png".equals(file.getContentType()) && startsWith(bytes, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
			boolean jpg = "image/jpeg".equals(file.getContentType()) && startsWith(bytes, new byte[]{(byte) 255, (byte) 216, (byte) 255});
			if (!png && !jpg) {
				throw new SocialAuthException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "등록증 촬영 이미지는 JPG, PNG만 사용할 수 있습니다.");
			}
			return bytes;
		} catch (IOException | RuntimeException exception) {
			if (bytes != null) { Arrays.fill(bytes, (byte) 0); }
			if (exception instanceof SocialAuthException social) { throw social; }
			throw new SocialAuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "등록증 촬영 이미지를 읽을 수 없습니다.");
		}
	}

	private boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
	}

	private SocialAuthException tooLarge() {
		return new SocialAuthException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "등록증 촬영 이미지는 5MB 이하여야 합니다.");
	}

	private SocialAuthException frontRequired() {
		return new SocialAuthException(HttpStatus.CONFLICT, "REGISTRATION_FRONT_REQUIRED", "등록증 앞면을 먼저 촬영해주세요. 확인 완료 후 재촬영도 앞면부터 진행해주세요.");
	}

	private String text(JsonNode node, String field) {
		if (!node.path(field).isString() || node.path(field).stringValue().isBlank()) { throw RegistrationOcrClient.unreadable(); }
		return node.path(field).stringValue().strip();
	}

	private LocalDate date(JsonNode node, String field) {
		try { return LocalDate.parse(text(node, field)); }
		catch (DateTimeParseException exception) { throw RegistrationOcrClient.unreadable(); }
	}

	private String countryCode(String value) {
		String normalized = value.strip().toUpperCase(Locale.ROOT);
		if (normalized.equals("VIET NAM")) { normalized = "VIETNAM"; }
		for (String code : Locale.getISOCountries()) {
			Locale locale = new Locale("", code);
			if (normalized.equals(code) || normalized.equals(locale.getISO3Country())
					|| normalized.equals(locale.getDisplayCountry(Locale.ENGLISH).toUpperCase(Locale.ROOT))
					|| normalized.equals(locale.getDisplayCountry(Locale.KOREAN))) { return code; }
		}
		throw RegistrationOcrClient.unreadable();
	}
}
