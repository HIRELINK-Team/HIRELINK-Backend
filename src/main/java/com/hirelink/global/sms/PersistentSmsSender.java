package com.hirelink.global.sms;

import java.time.Instant;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.hirelink.global.exception.DuplicateSmsException;
import com.hirelink.global.exception.InvalidSmsRequestException;
import com.hirelink.global.exception.SmsDeliveryException;

@Service
class PersistentSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(PersistentSmsSender.class);
	private static final Pattern RECIPIENT = Pattern.compile("[0-9]{8,15}");
	private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
	private final SmsSendHistoryRepository histories;
	private final ApplicationEventPublisher events;
	private final SmsGateway gateway;

	PersistentSmsSender(SmsSendHistoryRepository histories, ApplicationEventPublisher events, SmsGateway gateway) {
		this.histories = histories;
		this.events = events;
		this.gateway = gateway;
	}

	@Override
	@Transactional
	public void send(SmsSendRequest request) {
		SmsSendHistory history = createHistory(request);
		events.publishEvent(new SmsDeliveryRequested(history.getId(), request.recipient(), request.message()));
	}

	@Override
	@Transactional(noRollbackFor = SmsDeliveryException.class)
	public void sendAndWait(SmsSendRequest request) {
		SmsSendHistory history = createHistory(request);
		try {
			SmsGatewayResult result = gateway.send(request.recipient(), request.message());
			history.markSent(result.providerMessageId(), Instant.now());
		} catch (SmsDeliveryException exception) {
			history.markFailed(Instant.now());
			log.warn("SMS 발송에 실패했습니다. historyId={}, errorType={}", history.getId(),
					exception.getClass().getSimpleName());
			throw exception;
		}
	}

	private SmsSendHistory createHistory(SmsSendRequest request) {
		validate(request);
		SmsSendHistory history = new SmsSendHistory(request.recipient(), request.idempotencyKey(), Instant.now());
		try {
			return histories.saveAndFlush(history);
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicateSmsException();
		}
	}

	private void validate(SmsSendRequest request) {
		if (request == null || !StringUtils.hasText(request.recipient())
				|| !RECIPIENT.matcher(request.recipient()).matches()) {
			throw new InvalidSmsRequestException("수신번호 형식이 올바르지 않습니다.");
		}
		if (!StringUtils.hasText(request.message()) || request.message().length() > 2000) {
			throw new InvalidSmsRequestException("문자 내용은 1자 이상 2000자 이하여야 합니다.");
		}
		if (!StringUtils.hasText(request.idempotencyKey())
				|| !IDEMPOTENCY_KEY.matcher(request.idempotencyKey()).matches()) {
			throw new InvalidSmsRequestException("중복 방지 키 형식이 올바르지 않습니다.");
		}
	}
}
