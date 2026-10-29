package com.hirelink.global.exception;

public class PhoneVerificationRateLimitException extends RuntimeException {
	public PhoneVerificationRateLimitException() {
		super("인증 요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
	}
}
