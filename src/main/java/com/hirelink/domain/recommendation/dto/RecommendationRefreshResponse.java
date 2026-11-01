package com.hirelink.domain.recommendation.dto;

import java.time.Instant;

public record RecommendationRefreshResponse(
		Long batchId,
		Instant generatedAt,
		int recommendationCount,
		boolean refreshed) {
}
