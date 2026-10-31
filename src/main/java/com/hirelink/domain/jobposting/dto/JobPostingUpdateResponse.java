package com.hirelink.domain.jobposting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

public record JobPostingUpdateResponse(Long jobPostingId, Long workplaceId, String workplaceName, String title,
		Long businessTypeId, String businessTypeName, String jobRole, Integer recruitmentCount, String wageType,
		Integer wageAmount, LocalDate workStartDate, String requiredKoreanLevel, String preferredExperience,
		LocalDate applicationDeadlineDate, Boolean workTimeNegotiable, JobPostingStatus status,
		List<JobPostingWorkTimeResponse> workTimes, Instant updatedAt) {
}
