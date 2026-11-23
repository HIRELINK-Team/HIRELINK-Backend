package com.hirelink.domain.member.service;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import com.hirelink.domain.member.entity.TermType;
import com.hirelink.domain.member.entity.UserTermAgreement;
import com.hirelink.global.exception.SocialAuthException;

public final class TermVersionPolicy {
	private TermVersionPolicy() { }

	public static void requireCurrent(TermType term, String version) {
		if (term.getCurrentVersion() == null) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "TERM_VERSION_UNAVAILABLE", "약관 버전이 아직 준비되지 않았습니다.");
		}
		if (!term.getCurrentVersion().equals(version)) {
			throw new SocialAuthException(HttpStatus.CONFLICT, "TERM_VERSION_MISMATCH", "현재 약관 본문을 확인한 후 다시 동의해주세요.");
		}
	}

	public static void preserveExistingConsent(UserTermAgreement agreement, String version) {
		if (agreement.isAgreed() && !Objects.equals(agreement.getTermVersion(), version)) {
			throw new SocialAuthException(HttpStatus.CONFLICT, "TERM_RECONSENT_NOT_SUPPORTED", "기존 동의를 다른 버전으로 변경하는 기능은 제공하지 않습니다.");
		}
	}
}
