package com.hirelink.domain.employmentoffer.dto;

import java.time.Instant;

import com.hirelink.domain.application.entity.ApplicationEndReason;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

public record EmploymentOfferRejectResponse(Long offerId, Long applicationId,
		EmploymentOfferStatus offerStatus, ApplicationStatus applicationStatus,
		ApplicationEndReason endReason, Instant respondedAt, Instant endedAt) {
}
