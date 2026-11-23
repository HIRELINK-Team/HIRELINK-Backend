package com.hirelink.domain.student.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hirelink.domain.employmentmatch.repository.EmploymentMatchRepository;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.student.entity.StudentRegistrationVerification;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.global.sms.SmsSendRequest;
import com.hirelink.global.sms.SmsSender;

@Service
public class VisaExpirationSmsNotificationService {

	private static final Logger log = LoggerFactory.getLogger(VisaExpirationSmsNotificationService.class);
	private static final int MAX_EXPIRY_LOOKAHEAD_DAYS = 35;

	private final StudentRegistrationVerificationRepository registrations;
	private final EmploymentMatchRepository employmentMatches;
	private final SmsSender smsSender;

	public VisaExpirationSmsNotificationService(StudentRegistrationVerificationRepository registrations,
			EmploymentMatchRepository employmentMatches, SmsSender smsSender) {
		this.registrations = registrations;
		this.employmentMatches = employmentMatches;
		this.smsSender = smsSender;
	}

	public void sendDueNotifications(LocalDate today) {
		registrations.findAllWithUserAccountExpiringBetween(today, today.plusDays(MAX_EXPIRY_LOOKAHEAD_DAYS))
				.forEach(registration -> sendIfDue(registration, today));
	}

	private void sendIfDue(StudentRegistrationVerification registration, LocalDate today) {
		LocalDate expiryDate = registration.getStayExpiresOn();
		VisaExpirationSmsTiming timing = VisaExpirationSmsTiming.dueOn(expiryDate, today);
		if (timing == null) {
			return;
		}

		send(registration.getUserAccount(), registration.getUserId(), expiryDate, timing, timing.studentMessage(),
				"STUDENT");
		if (!timing.notifiesEmployer()) {
			return;
		}

		for (UserAccount employer : employmentMatches.findDistinctEmployerUsersByStudentProfileId(
				registration.getUserId())) {
			send(employer, registration.getUserId(), expiryDate, timing, timing.employerMessage(), "EMPLOYER");
		}
	}

	private void send(UserAccount recipient, Long studentId, LocalDate expiryDate,
			VisaExpirationSmsTiming timing, String message, String recipientType) {
		try {
			smsSender.send(new SmsSendRequest(recipient.getPhoneNumber(), message,
					idempotencyKey(studentId, expiryDate, timing, recipient.getId())));
		} catch (RuntimeException exception) {
			log.warn("비자 만료 문자 알림 등록에 실패했습니다. timing={}, recipientType={}, errorType={}",
					timing, recipientType, exception.getClass().getSimpleName());
		}
	}

	private String idempotencyKey(Long studentId, LocalDate expiryDate, VisaExpirationSmsTiming timing,
			Long recipientUserId) {
		return "visa-expiry:%d:%s:%s:%d".formatted(studentId, expiryDate, timing, recipientUserId);
	}
}
