package com.hirelink.domain.employmentoffer.dto;

import java.time.Instant;

import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용 제안 생성 결과")
public record EmploymentOfferCreateResponse(
		@Schema(description = "채용 제안 ID", example = "401", requiredMode = Schema.RequiredMode.REQUIRED)
		Long offerId,
		@Schema(description = "지원 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
		Long applicationId,
		@Schema(description = "채용 제안 상태: PENDING(응답 대기)", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
		EmploymentOfferStatus offerStatus,
		@Schema(description = "변경된 지원 상태: OFFERED(채용 제안 발송)", example = "OFFERED", requiredMode = Schema.RequiredMode.REQUIRED)
		ApplicationStatus applicationStatus,
		@Schema(description = "유학생 응답 기한", example = "2026-11-15T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant studentResponseDeadline,
		@Schema(description = "채용 제안 시각", example = "2026-11-14T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant offeredAt) {
}
