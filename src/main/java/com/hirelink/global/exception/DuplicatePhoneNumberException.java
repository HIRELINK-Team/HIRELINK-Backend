package com.hirelink.global.exception;

public class DuplicatePhoneNumberException extends RuntimeException {
	public DuplicatePhoneNumberException() {
		super("이미 사용 중인 휴대전화 번호입니다.");
	}
}
