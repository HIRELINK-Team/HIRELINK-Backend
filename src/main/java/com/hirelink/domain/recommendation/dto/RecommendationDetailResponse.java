package com.hirelink.domain.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecommendationDetailResponse(
		Long recommendationId,
		Long batchId,
		Long jobPostingId,
		int rank,
		Instant generatedAt,
		String jobTitle,
		String workplaceName,
		String workplaceAddress,
		String wageType,
		Integer wageAmount,
		List<RecommendationWorkTimeResponse> workTimes,
		BigDecimal matchScore,
		List<String> recommendationReasons) {
}
