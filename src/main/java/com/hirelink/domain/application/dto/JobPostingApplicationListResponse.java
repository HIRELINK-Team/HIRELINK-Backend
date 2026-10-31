package com.hirelink.domain.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.application.entity.ApplicationStatus;

public record JobPostingApplicationListResponse(
		Long jobPostingId,
		int capacity,
		long occupiedCount,
		long waitingResponseCount,
		long offeredCount,
		long completedCount,
		List<Item> applications) {

	public record Item(
			Long applicationId,
			ApplicationStatus status,
			String applicantName,
			String profileImageUrl,
			String nationality,
			String visaType,
			Short topikLevel,
			BigDecimal matchScore,
			LocalDate desiredStartDate,
			Instant appliedAt,
			Instant employerResponseDeadline,
			Long offerId,
			Instant studentResponseDeadline,
			Long matchId) {
	}
}
