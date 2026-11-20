package com.hirelink.domain.employmentnotification;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;

@Component
public class EmploymentSmsNotificationPublisher {

	private final ApplicationEventPublisher events;

	public EmploymentSmsNotificationPublisher(ApplicationEventPublisher events) {
		this.events = events;
	}

	public void applicationCreated(Application application) {
		publish(EmploymentSmsEventType.APPLICATION_CREATED, application.getId(),
				application.getJobPosting().getWorkplace().getEmployerUser().getPhoneNumber());
	}

	public void applicationRejected(Application application) {
		publish(EmploymentSmsEventType.APPLICATION_REJECTED, application.getId(),
				application.getStudentProfile().getUserAccount().getPhoneNumber());
	}

	public void offerCreated(EmploymentOffer offer) {
		publish(EmploymentSmsEventType.OFFER_CREATED, offer.getId(),
				offer.getApplication().getStudentProfile().getUserAccount().getPhoneNumber());
	}

	public void offerAccepted(EmploymentOffer offer) {
		publish(EmploymentSmsEventType.OFFER_ACCEPTED, offer.getId(),
				offer.getApplication().getJobPosting().getWorkplace().getEmployerUser().getPhoneNumber());
	}

	public void offerRejected(EmploymentOffer offer) {
		publish(EmploymentSmsEventType.OFFER_REJECTED, offer.getId(),
				offer.getApplication().getJobPosting().getWorkplace().getEmployerUser().getPhoneNumber());
	}

	private void publish(EmploymentSmsEventType type, Long entityId, String recipient) {
		events.publishEvent(new EmploymentSmsRequested(type, entityId, recipient));
	}
}
