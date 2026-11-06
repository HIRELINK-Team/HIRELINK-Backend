package com.hirelink.domain.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;
import com.hirelink.domain.employmentoffer.repository.EmploymentOfferRepository;

@Component
public class ApplicationExpirationScheduler {

	private final ApplicationRepository applicationRepository;
	private final EmploymentOfferRepository employmentOfferRepository;
	private final ApplicationExpirationProcessor expirationProcessor;
	private final Clock clock;

	public ApplicationExpirationScheduler(ApplicationRepository applicationRepository,
			EmploymentOfferRepository employmentOfferRepository,
			ApplicationExpirationProcessor expirationProcessor, Clock clock) {
		this.applicationRepository = applicationRepository;
		this.employmentOfferRepository = employmentOfferRepository;
		this.expirationProcessor = expirationProcessor;
		this.clock = clock;
	}

	@Scheduled(initialDelayString = "${application.expiration.initial-delay:0}",
			fixedDelayString = "${application.expiration.fixed-delay:60000}")
	public void expireResponses() {
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		List<Long> employerResponseIds = applicationRepository.findExpiredEmployerResponseIds(
				ApplicationStatus.WAITING_RESPONSE, now);
		employerResponseIds.forEach(id -> expirationProcessor.expireEmployerResponse(id, now));

		List<Long> studentResponseIds = employmentOfferRepository.findExpiredStudentResponseApplicationIds(
				EmploymentOfferStatus.PENDING, ApplicationStatus.OFFERED, now);
		studentResponseIds.forEach(id -> expirationProcessor.expireStudentResponse(id, now));
	}
}
