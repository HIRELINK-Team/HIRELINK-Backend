package com.hirelink.domain.auth.service;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.auth.dto.SocialSignupBirthDateResponse;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.global.exception.SocialAuthException;

@Service
public class SocialSignupBirthDateService {

	private final SocialAuthService auth;
	private final boolean enabled;

	public SocialSignupBirthDateService(SocialAuthService auth,
			@Value("${registration-verification.enabled:false}") boolean enabled) {
		this.auth = auth;
		this.enabled = enabled;
	}

	@Transactional
	public SocialSignupBirthDateResponse save(String token, LocalDate birthDate) {
		var session = auth.requireSignupSession(token);
		if (!enabled) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRATION_VERIFICATION_UNAVAILABLE", "등록증 확인 서비스를 이용할 수 없습니다.");
		}
		if (session.getRole() != MemberRole.STUDENT) {
			throw new SocialAuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "유학생 가입 중에만 이용할 수 있습니다.");
		}
		throw new SocialAuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
				"생년월일은 계정 생성 후 온보딩의 마치기에서 제출해주세요.");
	}
}
