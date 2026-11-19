package com.hirelink.global.sms;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class SmsDeliveryListener {

	private final SmsDeliveryProcessor processor;

	SmsDeliveryListener(SmsDeliveryProcessor processor) {
		this.processor = processor;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void deliver(SmsDeliveryRequested event) {
		processor.deliver(event.historyId(), event.recipient(), event.message());
	}
}
