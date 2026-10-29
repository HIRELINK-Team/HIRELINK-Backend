package com.hirelink.global.exception;

public class SmsDeliveryException extends RuntimeException {
	public SmsDeliveryException() {
		super("인증번호를 전송하지 못했습니다.");
	}
}
