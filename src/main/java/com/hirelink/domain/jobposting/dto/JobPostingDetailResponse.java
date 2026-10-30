package com.hirelink.domain.jobposting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

public record JobPostingDetailResponse(
		Long jobPostingId,
		String title,
		JobPostingStatus status,
		Long workplaceId,
		String workplaceName,
		String workplaceAddress,
		BigDecimal latitude,
		BigDecimal longitude,
		Long businessTypeId,
		String businessTypeName,
		String jobRole,
		String wageType,
		Integer wageAmount,
		Integer recruitmentCount,
		LocalDate workStartDate,
		String requiredKoreanLevel,
		String preferredExperience,
		Boolean workTimeNegotiable,
		LocalDate applicationDeadlineDate,
		List<JobPostingWorkTimeResponse> workTimes) {
}
