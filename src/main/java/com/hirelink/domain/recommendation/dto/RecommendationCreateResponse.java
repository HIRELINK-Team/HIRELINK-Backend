package com.hirelink.domain.recommendation.dto;

import java.time.Instant;

public record RecommendationCreateResponse(
		Long batchId,
		Instant generatedAt,
		int recommendationCount) {
}
