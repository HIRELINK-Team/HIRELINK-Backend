package com.hirelink.domain.jobposting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

public record JobPostingListResponse(List<JobPostingSummary> jobPostings) {

	public record JobPostingSummary(
			Long jobPostingId,
			Long workplaceId,
			String workplaceName,
			String jobRole,
			String wageType,
			Integer wageAmount,
			JobPostingStatus status,
			LocalDate applicationDeadlineDate,
			List<WorkTime> workTimes,
			Instant createdAt) {
	}

	public record WorkTime(
			String dayOfWeek,
			@JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
	}
}
