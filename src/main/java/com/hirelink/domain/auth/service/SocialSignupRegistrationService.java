package com.hirelink.domain.auth.service;

import static com.hirelink.domain.auth.service.RegistrationDocumentSupport.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hirelink.domain.auth.client.ApickRegistrationClient;
import com.hirelink.domain.auth.client.RegistrationOcrClient;
import com.hirelink.domain.auth.dto.RegistrationFrontResponse;
import com.hirelink.domain.auth.dto.RegistrationVerificationResponse;
import com.hirelink.domain.auth.entity.SocialAuthSession;
import com.hirelink.domain.auth.entity.SocialSignupRegistration;
import com.hirelink.domain.auth.repository.SocialSignupRegistrationRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.TermType;
import com.hirelink.domain.student.entity.StudyProgramType;
import com.hirelink.global.exception.SocialAuthException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SocialSignupRegistrationService {

	static void requireSupportedVisa(String visaCode) {
		if (!StudyProgramType.supportsRegistration(visaCode)) {
			throw new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_VISA",
					"등록증에서 확인한 체류자격이 D-2여야 합니다.");
		}
	}

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
			registration = registrations.saveAndFlush(registration);
			JsonNode result = ocr.recognize(bytes, file.getContentType(), true);
			String name = text(result, "name");
			String country = countryCode(text(result, "nationality"));
			String visa = text(result, "visaCode");
			String number = text(result, "registrationNumber");
			LocalDate issued = date(result, "issueDate");
			if (name.length() > 100 || !VISAS.contains(visa) || !number.matches("[0-9]{6}[5-8][0-9]{6}")
					|| issued.isAfter(LocalDate.now(KOREA))) { throw RegistrationOcrClient.unreadable(); }
			requireSupportedVisa(visa);
			requireUnexpired(session, registration);
			ObjectNode payload = mapper.createObjectNode();
			payload.put("name", name).put("nationalityCode", country).put("visaCode", visa)
					.put("registrationNumber", number).put("issueDate", issued.toString());
			registration.saveFront(cipher.encrypt(mapper.writeValueAsString(payload), session.getTokenHash()));
			return new RegistrationFrontResponse(name, country, visa, number.substring(0, 6) + "-" + number.substring(6), issued, session.getExpiresAt());
		} finally {
			Arrays.fill(bytes, (byte) 0);
		}
	}

	@Transactional(noRollbackFor = SocialAuthException.class)
	public RegistrationVerificationResponse back(String token, MultipartFile file) {
		SocialAuthSession session = requireSession(token);
		SocialSignupRegistration registration = registrations.findById(session.getTokenHash()).orElseThrow(this::frontRequired);
		if (registration.getFrontPayload() == null) { throw frontRequired(); }
		JsonNode front = mapper.readTree(cipher.decrypt(registration.getFrontPayload(), session.getTokenHash()));
		requireSupportedVisa(text(front, "visaCode"));
		if (!front.path("registrationNumber").isString() || !front.path("issueDate").isString()) { throw frontRequired(); }
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
			((ObjectNode) front).remove("issueDate");
			// PDF 동일인 확인과 공통 응답 연결에 필요한 동안만 번호를 보관한다.
			if (requiresPdf) {
				((ObjectNode) front).put("serialNumber", serialNumber);
			} else {
				((ObjectNode) front).remove("registrationNumber");
			}
			registration.verify(cipher.encrypt(mapper.writeValueAsString(front), session.getTokenHash()), expiry, Instant.now());
			return new RegistrationVerificationResponse(serialNumber, expiry, true, false, requiresPdf, session.getExpiresAt());
		} finally {
			Arrays.fill(bytes, (byte) 0);
		}
	}

	@Transactional(noRollbackFor = SocialAuthException.class)
	public RegistrationVerificationResponse stayDocument(String token, MultipartFile file) {
		SocialAuthSession session = requireSession(token);
		SocialSignupRegistration registration = registrations.findById(session.getTokenHash()).orElseThrow(this::backRequired);
		if (registration.getCardVerifiedAt() == null) { throw backRequired(); }
		if (!registration.getStayExpiresOn().isBefore(LocalDate.now(KOREA))) {
			throw new SocialAuthException(HttpStatus.CONFLICT, "STAY_DOCUMENT_NOT_REQUIRED", "카드의 체류기간이 유효하여 PDF 제출이 필요하지 않습니다.");
		}
		ObjectNode front = (ObjectNode) mapper.readTree(cipher.decrypt(registration.getFrontPayload(), session.getTokenHash()));
		requireSupportedVisa(text(front, "visaCode"));
		if (!front.path("registrationNumber").isString()) { throw frontRequired(); }
		JsonNode serial = front.get("serialNumber");
		// 구형 카드의 명시적 null과 일련번호를 보관하지 않던 이전 세션을 구분한다.
		if (serial == null || (!serial.isNull() && (!serial.isString() || !serial.stringValue().matches("[0-9]{11}")))) {
			throw frontRequired();
		}
		String serialNumber = serial.isNull() ? null : serial.stringValue();
		byte[] bytes = readFile(file, true);
		try {
			registration.resetDocument();
			registrations.flush();
			JsonNode result = ocr.recognizeStayDocument(bytes);
			requireUnexpired(session, registration);
			LocalDate expiry;
			String number;
			try {
				number = text(result, "registrationNumber");
				expiry = date(result, "stayExpiresOn");
			} catch (SocialAuthException exception) { throw RegistrationOcrClient.unreadableDocument(); }
			if (!number.matches("[0-9]{6}[5-8][0-9]{6}")) { throw RegistrationOcrClient.unreadableDocument(); }
			if (!number.equals(text(front, "registrationNumber"))) {
				throw new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY, "STAY_DOCUMENT_MISMATCH", "등록증과 증빙서류의 등록번호가 일치하지 않습니다. 본인의 PDF를 다시 선택해주세요.");
			}
			if (expiry.isBefore(LocalDate.now(KOREA))) {
				throw new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY, "STAY_DOCUMENT_EXPIRED", "증빙서류의 체류기간이 지났습니다. 유효한 PDF를 다시 선택해주세요.");
			}
			front.remove("registrationNumber");
			front.remove("issueDate");
			front.remove("serialNumber");
			registration.verifyDocument(cipher.encrypt(mapper.writeValueAsString(front), session.getTokenHash()), expiry, Instant.now());
			return new RegistrationVerificationResponse(serialNumber, expiry, true, true, false, session.getExpiresAt());
		} finally { Arrays.fill(bytes, (byte) 0); }
	}

	private SocialAuthException backRequired() {
		return new SocialAuthException(HttpStatus.CONFLICT, "REGISTRATION_BACK_REQUIRED", "등록증 앞면과 뒷면의 진위 확인을 먼저 완료해주세요.");
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
					agreement.getTermTypeId().equals(term.getId()) && agreement.isAgreed() && agreement.getAgreedAt() != null && agreement.getTermVersion() != null)) {
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

	private SocialAuthException frontRequired() {
		return new SocialAuthException(HttpStatus.CONFLICT, "REGISTRATION_FRONT_REQUIRED", "등록증 앞면을 먼저 촬영해주세요. 확인 완료 후 재촬영도 앞면부터 진행해주세요.");
	}
}
