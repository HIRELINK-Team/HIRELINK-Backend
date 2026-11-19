package com.hirelink.global.exception;

public class SmsDeliveryException extends RuntimeException {
	public SmsDeliveryException() {
		super("문자를 발송하지 못했습니다.");
	}
}
