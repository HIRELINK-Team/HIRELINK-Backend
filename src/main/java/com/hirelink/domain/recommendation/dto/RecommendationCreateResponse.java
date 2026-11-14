package com.hirelink.domain.recommendation.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최초 추천 생성 결과")
public record RecommendationCreateResponse(
		@Schema(description = "생성된 추천 배치 ID", example = "11", requiredMode = Schema.RequiredMode.REQUIRED)
		Long batchId,
		@Schema(description = "추천 생성 시각", example = "2026-11-14T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant generatedAt,
		@Schema(description = "생성된 추천 수", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
		int recommendationCount) {
}
