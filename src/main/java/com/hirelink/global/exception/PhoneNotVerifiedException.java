package com.hirelink.global.exception;

public class PhoneNotVerifiedException extends RuntimeException {

	public PhoneNotVerifiedException() {
		super("휴대전화 인증을 먼저 완료해주세요.");
	}
}
