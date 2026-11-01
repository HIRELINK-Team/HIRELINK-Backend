package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

public record RecommendationCandidate(
		Long jobPostingId,
		JobPostingStatus status,
		LocalDate applicationDeadlineDate,
		long activeReviewCount,
		boolean previouslyApplied,
		BigDecimal workplaceLatitude,
		BigDecimal workplaceLongitude,
		String wageType,
		Integer wageAmount,
		String businessTypeName,
		String otherJobName,
		List<RecommendationTimeRange> requiredWorkTimes) {

	public RecommendationCandidate {
		requiredWorkTimes = requiredWorkTimes == null ? List.of() : List.copyOf(requiredWorkTimes);
	}
}
