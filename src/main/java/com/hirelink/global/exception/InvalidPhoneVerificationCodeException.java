package com.hirelink.global.exception;

public class InvalidPhoneVerificationCodeException extends RuntimeException {
	public InvalidPhoneVerificationCodeException() {
		super("인증번호가 일치하지 않거나 만료되었습니다.");
	}
}
