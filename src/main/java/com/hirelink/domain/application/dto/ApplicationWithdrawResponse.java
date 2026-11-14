package com.hirelink.domain.application.dto;

import java.time.Instant;

import com.hirelink.domain.application.entity.ApplicationEndReason;
import com.hirelink.domain.application.entity.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유학생 지원 취소 결과")
public record ApplicationWithdrawResponse(
		@Schema(description = "지원 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
		Long applicationId,
		@Schema(description = "변경된 지원 상태: ENDED(지원 종료)", example = "ENDED", requiredMode = Schema.RequiredMode.REQUIRED)
		ApplicationStatus status,
		@Schema(description = "종료 사유", example = "WITHDRAWN", requiredMode = Schema.RequiredMode.REQUIRED)
		ApplicationEndReason endReason,
		@Schema(description = "지원 종료 시각", example = "2026-11-14T04:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant endedAt) {
}
