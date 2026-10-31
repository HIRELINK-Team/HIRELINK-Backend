package com.hirelink.domain.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.hirelink.domain.application.entity.ApplicationEndReason;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

public record StudentApplicationDetailResponse(
		Long applicationId,
		ApplicationStatus status,
		Long jobPostingId,
		String jobTitle,
		String workplaceName,
		String workplaceAddress,
		String wageType,
		Integer wageAmount,
		List<WorkTime> workTimes,
		LocalDate desiredStartDate,
		Instant appliedAt,
		Instant employerResponseDeadline,
		Long offerId,
		EmploymentOfferStatus offerStatus,
		Instant offeredAt,
		Instant studentResponseDeadline,
		Instant respondedAt,
		Long matchId,
		ApplicationEndReason endReason,
		Instant endedAt) {

	public record WorkTime(
			String dayOfWeek,
			@JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
	}
}
