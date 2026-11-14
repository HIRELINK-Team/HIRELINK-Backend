package com.hirelink.domain.recommendation.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 갱신 결과")
public record RecommendationRefreshResponse(
		@Schema(description = "현재 추천 배치 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
		Long batchId,
		@Schema(description = "현재 추천 생성 시각", example = "2026-11-14T03:10:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant generatedAt,
		@Schema(description = "현재 추천 수", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
		int recommendationCount,
		@Schema(description = "새 추천 배치 생성 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
		boolean refreshed) {
}
