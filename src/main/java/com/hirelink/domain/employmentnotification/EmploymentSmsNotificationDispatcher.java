package com.hirelink.domain.employmentnotification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.global.sms.SmsSendRequest;
import com.hirelink.global.sms.SmsSender;

@Service
class EmploymentSmsNotificationDispatcher {

	private final SmsSender smsSender;

	EmploymentSmsNotificationDispatcher(SmsSender smsSender) {
		this.smsSender = smsSender;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void send(EmploymentSmsRequested event) {
		smsSender.send(new SmsSendRequest(event.recipient(), event.type().message(),
				event.type().idempotencyKey(event.entityId())));
	}
}
