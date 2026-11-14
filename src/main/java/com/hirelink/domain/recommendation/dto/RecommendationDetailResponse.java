package com.hirelink.domain.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 상세 정보")
public record RecommendationDetailResponse(
		@Schema(description = "추천 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
		Long recommendationId,
		@Schema(description = "추천 배치 ID", example = "11", requiredMode = Schema.RequiredMode.REQUIRED)
		Long batchId,
		@Schema(description = "채용공고 ID", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
		Long jobPostingId,
		@Schema(description = "배치 내 추천 순위", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		int rank,
		@Schema(description = "추천 생성 시각", example = "2026-11-14T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant generatedAt,
		@Schema(description = "채용공고 제목", example = "카페 주말 스태프", requiredMode = Schema.RequiredMode.REQUIRED)
		String jobTitle,
		@Schema(description = "사업장 이름", example = "하이어링크 카페", requiredMode = Schema.RequiredMode.REQUIRED)
		String workplaceName,
		@Schema(description = "사업장 주소", example = "서울특별시 중구 세종대로 1", requiredMode = Schema.RequiredMode.REQUIRED)
		String workplaceAddress,
		@Schema(description = "급여 유형", example = "HOURLY", requiredMode = Schema.RequiredMode.REQUIRED)
		String wageType,
		@Schema(description = "급여 금액(원)", example = "12000", requiredMode = Schema.RequiredMode.REQUIRED)
		Integer wageAmount,
		@Schema(description = "근무시간 목록", requiredMode = Schema.RequiredMode.REQUIRED)
		List<RecommendationWorkTimeResponse> workTimes,
		@Schema(description = "추천 매칭 점수", example = "92.50", requiredMode = Schema.RequiredMode.REQUIRED)
		BigDecimal matchScore,
		@Schema(description = "추천 이유 목록", example = "[\"희망 근무시간 일치\", \"희망 거리 충족\"]", requiredMode = Schema.RequiredMode.REQUIRED)
		List<String> recommendationReasons) {
}
