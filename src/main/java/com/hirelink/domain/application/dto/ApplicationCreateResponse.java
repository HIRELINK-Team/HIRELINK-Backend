package com.hirelink.domain.application.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.hirelink.domain.application.entity.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원 생성 결과")
public record ApplicationCreateResponse(
		@Schema(description = "지원 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
		Long applicationId,
		@Schema(description = "지원에 사용한 추천 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
		Long recommendationId,
		@Schema(description = "채용공고 ID", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
		Long jobPostingId,
		@Schema(description = "희망 근무 시작일", example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalDate desiredStartDate,
		@Schema(description = "지원 상태: WAITING_RESPONSE(사업주 응답 대기), OFFERED(채용 제안 발송), COMPLETED(채용 완료), ENDED(지원 종료)", example = "WAITING_RESPONSE", requiredMode = Schema.RequiredMode.REQUIRED)
		ApplicationStatus status,
		@Schema(description = "사업주 응답 기한", example = "2026-11-15T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant employerResponseDeadline,
		@Schema(description = "지원 시각", example = "2026-11-14T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant appliedAt) {
}
