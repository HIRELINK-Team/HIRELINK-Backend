package com.hirelink.domain.auth.service;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.auth.dto.SocialSignupBirthDateResponse;
import com.hirelink.domain.auth.entity.SocialAuthSession;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.global.exception.SocialAuthException;

@Service
public class SocialSignupBirthDateService {

	private final SocialAuthService auth;
	private final SocialSignupTermsService terms;
	private final SocialSignupRegistrationTransferService registrationTransfer;
	private final boolean enabled;

	public SocialSignupBirthDateService(SocialAuthService auth, SocialSignupTermsService terms,
			SocialSignupRegistrationTransferService registrationTransfer,
			@Value("${registration-verification.enabled:false}") boolean enabled) {
		this.auth = auth;
		this.terms = terms;
		this.registrationTransfer = registrationTransfer;
		this.enabled = enabled;
	}

	@Transactional
	public SocialSignupBirthDateResponse save(String token, LocalDate birthDate) {
		SocialAuthSession session = auth.requireSignupSession(token);
		if (!enabled) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRATION_VERIFICATION_UNAVAILABLE", "등록증 확인 서비스를 이용할 수 없습니다.");
		}
		if (session.getRole() != MemberRole.STUDENT) {
			throw new SocialAuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "유학생 가입 중에만 생년월일을 저장할 수 있습니다.");
		}
		for (var term : terms.signupTerms(session)) {
			if (term.isRequired() && session.getAgreements().stream().noneMatch(agreement ->
					agreement.getTermTypeId().equals(term.getId()) && agreement.isAgreed() && agreement.getAgreedAt() != null)) {
				throw new SocialAuthException(HttpStatus.BAD_REQUEST, "REQUIRED_TERMS_NOT_AGREED", "필수 약관에 모두 동의해주세요.");
			}
		}
		registrationTransfer.requireVerified(session);
		validateAge(birthDate);
		session.saveBirthDate(birthDate);
		return new SocialSignupBirthDateResponse(true, session.getExpiresAt());
	}

	void requireForPhoneSignup(SocialAuthSession session) {
		if (enabled && session.getRole() == MemberRole.STUDENT) {
			validateAge(session.getBirthDate());
		}
	}

	private void validateAge(LocalDate birthDate) {
		if (birthDate == null) {
			throw new SocialAuthException(HttpStatus.CONFLICT, "BIRTH_DATE_REQUIRED", "생년월일을 먼저 입력해주세요.");
		}
		if (birthDate.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(18))) {
			throw new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY, "AGE_REQUIREMENT_NOT_MET", "만 18세 이상만 가입할 수 있습니다.");
		}
	}
}
