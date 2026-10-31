package com.hirelink.domain.application.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.hirelink.domain.application.entity.ApplicationStatus;

public record ApplicationCreateResponse(
		Long applicationId,
		Long recommendationId,
		Long jobPostingId,
		LocalDate desiredStartDate,
		ApplicationStatus status,
		Instant employerResponseDeadline,
		Instant appliedAt) {
}
