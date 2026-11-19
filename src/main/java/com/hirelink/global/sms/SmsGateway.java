package com.hirelink.global.sms;

interface SmsGateway {

	SmsGatewayResult send(String recipient, String message);
}
