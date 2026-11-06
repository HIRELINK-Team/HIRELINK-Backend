package com.hirelink.domain.application.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;
import com.hirelink.domain.employmentoffer.repository.EmploymentOfferRepository;

@Service
public class ApplicationExpirationProcessor {

	private final ApplicationRepository applicationRepository;
	private final EmploymentOfferRepository employmentOfferRepository;

	public ApplicationExpirationProcessor(ApplicationRepository applicationRepository,
			EmploymentOfferRepository employmentOfferRepository) {
		this.applicationRepository = applicationRepository;
		this.employmentOfferRepository = employmentOfferRepository;
	}

	@Transactional
	public void expireEmployerResponse(Long applicationId, Instant now) {
		Application application = applicationRepository.findByIdForUpdate(applicationId).orElse(null);
		if (application == null || application.getStatus() != ApplicationStatus.WAITING_RESPONSE
				|| application.getEmployerResponseDeadline().isAfter(now)) {
			return;
		}
		application.expireEmployerResponse(now);
	}

	@Transactional
	public void expireStudentResponse(Long applicationId, Instant now) {
		Application application = applicationRepository.findByIdForUpdate(applicationId).orElse(null);
		if (application == null || application.getStatus() != ApplicationStatus.OFFERED) {
			return;
		}
		EmploymentOffer offer = employmentOfferRepository.findByApplicationIdForUpdate(applicationId).orElse(null);
		if (offer == null || offer.getStatus() != EmploymentOfferStatus.PENDING
				|| offer.getStudentResponseDeadline().isAfter(now)) {
			return;
		}
		offer.expireStudentResponse(now);
		application.expireStudentResponse(now);
	}
}
