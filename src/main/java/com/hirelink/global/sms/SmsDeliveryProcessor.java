package com.hirelink.global.sms;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.global.exception.SmsDeliveryException;

@Service
class SmsDeliveryProcessor {

	private static final Logger log = LoggerFactory.getLogger(SmsDeliveryProcessor.class);
	private final SmsSendHistoryRepository histories;
	private final SmsGateway gateway;

	SmsDeliveryProcessor(SmsSendHistoryRepository histories, SmsGateway gateway) {
		this.histories = histories;
		this.gateway = gateway;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deliver(Long historyId, String recipient, String message) {
		SmsSendHistory history = histories.findById(historyId).orElse(null);
		if (history == null || history.getStatus() != SmsSendStatus.PENDING) {
			return;
		}
		try {
			SmsGatewayResult result = gateway.send(recipient, message);
			history.markSent(result.providerMessageId(), Instant.now());
		} catch (SmsDeliveryException exception) {
			history.markFailed(Instant.now());
			log.warn("SMS 발송에 실패했습니다. historyId={}, errorType={}", historyId,
					exception.getClass().getSimpleName());
		}
	}
}
