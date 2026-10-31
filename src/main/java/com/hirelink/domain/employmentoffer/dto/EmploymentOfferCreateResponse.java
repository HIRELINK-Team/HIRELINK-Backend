package com.hirelink.domain.employmentoffer.dto;

import java.time.Instant;

import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

public record EmploymentOfferCreateResponse(Long offerId, Long applicationId,
		EmploymentOfferStatus offerStatus, ApplicationStatus applicationStatus,
		Instant studentResponseDeadline, Instant offeredAt) {
}
