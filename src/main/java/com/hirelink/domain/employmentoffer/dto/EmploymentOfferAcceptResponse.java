package com.hirelink.domain.employmentoffer.dto;

import java.time.Instant;

import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

public record EmploymentOfferAcceptResponse(Long offerId, Long applicationId, Long employmentMatchId,
		EmploymentOfferStatus offerStatus, ApplicationStatus applicationStatus,
		Instant respondedAt, Instant completedAt) {
}
