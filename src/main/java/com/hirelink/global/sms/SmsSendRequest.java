package com.hirelink.global.sms;

public record SmsSendRequest(String recipient, String message, String idempotencyKey) {

	@Override
	public String toString() {
		return "SmsSendRequest[recipient=***, message=***, idempotencyKey=***]";
	}
}
