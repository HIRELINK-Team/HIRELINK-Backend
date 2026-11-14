package com.hirelink.domain.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.hirelink.domain.application.entity.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최신 추천 목록")
public record RecommendationListResponse(
		@Schema(description = "최신 추천 배치 ID", example = "11", requiredMode = Schema.RequiredMode.REQUIRED)
		Long batchId,
		@Schema(description = "추천 생성 시각", example = "2026-11-14T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant generatedAt,
		@Schema(description = "현재 조건에 맞는 수동 갱신 필요 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
		boolean refreshRequired,
		@Schema(description = "순위순 추천 목록", requiredMode = Schema.RequiredMode.REQUIRED)
		List<Item> recommendations) {

	@Schema(description = "추천 목록 항목")
	public record Item(
			@Schema(description = "추천 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
			Long recommendationId,
			@Schema(description = "채용공고 ID", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
			Long jobPostingId,
			@Schema(description = "해당 공고에 연결된 지원 ID. 지원하지 않았으면 null", example = "201", nullable = true)
			Long applicationId,
			@Schema(description = "연결된 지원 상태: WAITING_RESPONSE(사업주 응답 대기), OFFERED(채용 제안 발송), COMPLETED(채용 완료), ENDED(지원 종료). 지원하지 않았으면 null", example = "WAITING_RESPONSE", nullable = true)
			ApplicationStatus applicationStatus,
			@Schema(description = "추천 순위", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
			int rank,
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
}
