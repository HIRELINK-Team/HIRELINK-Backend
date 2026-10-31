package com.hirelink.domain.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

public record StudentApplicationListResponse(List<Item> applications) {

	public record Item(
			Long applicationId,
			ApplicationStatus status,
			Long jobPostingId,
			String jobTitle,
			String workplaceName,
			String wageType,
			Integer wageAmount,
			List<String> recommendationReasons,
			LocalDate desiredStartDate,
			Instant appliedAt,
			Instant employerResponseDeadline,
			Long offerId,
			EmploymentOfferStatus offerStatus,
			Instant offeredAt,
			Instant studentResponseDeadline,
			Long matchId) {
	}
}
