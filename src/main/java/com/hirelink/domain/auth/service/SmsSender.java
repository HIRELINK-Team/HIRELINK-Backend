package com.hirelink.domain.auth.service;

import org.springframework.stereotype.Component;

import com.hirelink.global.exception.SmsDeliveryException;

@Component
public class SmsSender {

	public void sendVerificationCode(String phoneNumber, String code) {
		// SMS 업체 연동 전에는 전송 성공으로 처리하지 않는다.
		throw new SmsDeliveryException();
	}
}
