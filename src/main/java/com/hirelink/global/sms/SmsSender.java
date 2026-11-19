package com.hirelink.global.sms;

public interface SmsSender {

	void send(SmsSendRequest request);

	void sendAndWait(SmsSendRequest request);
}
