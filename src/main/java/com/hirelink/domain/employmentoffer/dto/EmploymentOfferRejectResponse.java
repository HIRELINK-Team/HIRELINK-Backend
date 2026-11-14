package com.hirelink.domain.employmentoffer.dto;

import java.time.Instant;

import com.hirelink.domain.application.entity.ApplicationEndReason;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용 제안 거절 결과")
public record EmploymentOfferRejectResponse(
		@Schema(description = "채용 제안 ID", example = "401", requiredMode = Schema.RequiredMode.REQUIRED)
		Long offerId,
		@Schema(description = "지원 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
		Long applicationId,
		@Schema(description = "변경된 채용 제안 상태: REJECTED(거절)", example = "REJECTED", requiredMode = Schema.RequiredMode.REQUIRED)
		EmploymentOfferStatus offerStatus,
		@Schema(description = "변경된 지원 상태: ENDED(지원 종료)", example = "ENDED", requiredMode = Schema.RequiredMode.REQUIRED)
		ApplicationStatus applicationStatus,
		@Schema(description = "지원 종료 사유", example = "STUDENT_REJECTED", requiredMode = Schema.RequiredMode.REQUIRED)
		ApplicationEndReason endReason,
		@Schema(description = "제안 응답 시각", example = "2026-11-14T05:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant respondedAt,
		@Schema(description = "제안 및 지원 종료 시각", example = "2026-11-14T05:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant endedAt) {
}
