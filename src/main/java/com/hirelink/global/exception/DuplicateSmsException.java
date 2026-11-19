package com.hirelink.global.exception;

public class DuplicateSmsException extends RuntimeException {
	public DuplicateSmsException() {
		super("이미 처리된 문자 발송 요청입니다.");
	}
}
