package com.hirelink.domain.jobposting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
			List<JobPostingWorkTimeResponse> workTimes,
			Instant createdAt) {
	}
}
