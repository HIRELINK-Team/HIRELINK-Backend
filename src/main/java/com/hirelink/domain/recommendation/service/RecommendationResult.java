package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RecommendationResult(
		Long jobPostingId,
		BigDecimal matchScore,
		Map<RecommendationCriterion, BigDecimal> criterionScores,
		double distanceKm,
		List<String> reasons) {

	public RecommendationResult {
		criterionScores = Map.copyOf(criterionScores);
		reasons = List.copyOf(reasons);
	}
}
