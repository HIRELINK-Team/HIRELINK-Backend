package com.hirelink.domain.auth.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.hirelink.domain.auth.entity.SocialAuthSession;
import com.hirelink.domain.auth.repository.SocialSignupRegistrationRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.student.entity.StudentRegistrationVerification;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.global.exception.SocialAuthException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SocialSignupRegistrationTransferService {

	private final SocialSignupRegistrationRepository registrations;
	private final StudentRegistrationVerificationRepository verifiedRegistrations;
	private final RegistrationCipher cipher;
	private final ObjectMapper mapper;
	private final boolean enabled;

	public SocialSignupRegistrationTransferService(SocialSignupRegistrationRepository registrations,
			StudentRegistrationVerificationRepository verifiedRegistrations, RegistrationCipher cipher,
			ObjectMapper mapper, @Value("${registration-verification.enabled:false}") boolean enabled) {
		this.registrations = registrations;
		this.verifiedRegistrations = verifiedRegistrations;
		this.cipher = cipher;
		this.mapper = mapper;
		this.enabled = enabled;
	}

	// 호출자의 트랜잭션과 소셜 사용자 → 가입 세션 잠금 안에서 검사한다.
	VerifiedRegistration requireVerified(SocialAuthSession session) {
		if (!enabled || session.getRole() != MemberRole.STUDENT) { return null; }
		var registration = registrations.findById(session.getTokenHash()).orElseThrow(this::required);
		if (registration.getCardVerifiedAt() == null || registration.getFrontPayload() == null) { throw required(); }
		JsonNode front = mapper.readTree(cipher.decrypt(registration.getFrontPayload(), session.getTokenHash()));
		SocialSignupRegistrationService.requireSupportedVisa(field(front, "visaCode"));
		LocalDate expiry = registration.getStayExpiresOn();
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		Instant documentVerifiedAt = null;
		if (expiry.isBefore(today)) {
			if (registration.getDocumentVerifiedAt() == null) {
				if (!front.has("registrationNumber")) { throw required(); }
				throw new SocialAuthException(HttpStatus.CONFLICT, "STAY_DOCUMENT_REQUIRED", "체류기간 증빙 PDF 확인을 먼저 완료해주세요.");
			}
			expiry = registration.getDocumentExpiresOn();
			documentVerifiedAt = registration.getDocumentVerifiedAt();
			if (expiry.isBefore(today)) {
				throw new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY, "STAY_DOCUMENT_EXPIRED", "증빙서류의 체류기간이 지났습니다. 등록증 앞면부터 다시 확인해주세요.");
			}
		}
		return new VerifiedRegistration(field(front, "name"), field(front, "nationalityCode"), field(front, "visaCode"),
				expiry, registration.getCardVerifiedAt(), documentVerifiedAt);
	}

	void save(UserAccount user, VerifiedRegistration registration, Instant registeredAt) {
		if (registration == null) { return; }
		user.updateName(registration.name());
		verifiedRegistrations.saveAndFlush(new StudentRegistrationVerification(user, registration.nationality(),
				registration.visaCode(), registration.expiry(), registration.cardVerifiedAt(), registration.documentVerifiedAt(), registeredAt, null));
	}

	private String field(JsonNode front, String name) {
		if (!front.path(name).isString() || front.path(name).stringValue().isBlank()) { throw required(); }
		return front.path(name).stringValue();
	}

	private SocialAuthException required() {
		return new SocialAuthException(HttpStatus.CONFLICT, "REGISTRATION_VERIFICATION_REQUIRED", "등록증 앞면부터 진위 확인을 완료해주세요.");
	}

	record VerifiedRegistration(String name, String nationality, String visaCode, LocalDate expiry,
			Instant cardVerifiedAt, Instant documentVerifiedAt) {
		@Override public String toString() { return "VerifiedRegistration[redacted]"; }
	}
}
