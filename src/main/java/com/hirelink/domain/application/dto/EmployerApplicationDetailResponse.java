package com.hirelink.domain.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.hirelink.domain.application.entity.ApplicationStatus;

public record EmployerApplicationDetailResponse(
		Long applicationId,
		Long jobPostingId,
		ApplicationStatus status,
		LocalDate desiredStartDate,
		Instant appliedAt,
		Instant employerResponseDeadline,
		Applicant applicant,
		JobRequirements jobRequirements,
		MatchResult matchResult) {

	public record Applicant(
			String name,
			String profileImageUrl,
			String nationality,
			String visaType,
			LocalDate visaExpirationDate,
			String koreanLevel,
			Short topikLevel,
			List<Career> careers,
			List<WorkTime> availableWorkTimes) {
	}

	public record Career(String careerName, String startMonth, String endMonth, String description) {
	}

	public record WorkTime(
			String dayOfWeek,
			@JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
	}

	public record JobRequirements(
			List<WorkTime> workTimes,
			Integer wageAmount,
			String requiredKoreanLevel) {
	}

	public record MatchResult(
			BigDecimal matchScore,
			boolean workTimeMatched,
			boolean experienceMatched,
			boolean locationMatched,
			boolean wageMatched,
			boolean koreanLevelMatched) {
	}
}
