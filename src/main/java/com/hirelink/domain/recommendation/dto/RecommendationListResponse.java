package com.hirelink.domain.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecommendationListResponse(
		Long batchId,
		Instant generatedAt,
		boolean refreshRequired,
		List<Item> recommendations) {

	public record Item(
			Long recommendationId,
			Long jobPostingId,
			int rank,
			String jobTitle,
			String workplaceName,
			String workplaceAddress,
			String wageType,
			Integer wageAmount,
			List<RecommendationWorkTimeResponse> workTimes,
			BigDecimal matchScore,
			List<String> recommendationReasons) {
	}
}
