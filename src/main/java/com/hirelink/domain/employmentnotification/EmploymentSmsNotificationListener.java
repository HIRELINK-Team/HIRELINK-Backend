package com.hirelink.domain.employmentnotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class EmploymentSmsNotificationListener {

	private static final Logger log = LoggerFactory.getLogger(EmploymentSmsNotificationListener.class);
	private final EmploymentSmsNotificationDispatcher dispatcher;

	EmploymentSmsNotificationListener(EmploymentSmsNotificationDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void send(EmploymentSmsRequested event) {
		try {
			dispatcher.send(event);
		} catch (RuntimeException exception) {
			log.warn("채용 문자 알림 등록에 실패했습니다. eventType={}, entityId={}, errorType={}",
					event.type(), event.entityId(), exception.getClass().getSimpleName());
		}
	}
}
