package com.hirelink.domain.developerinquiry.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개발자 문의 접수 결과")
public record DeveloperInquiryCreateResponse(
		@Schema(description = "접수된 문의 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
		Long inquiryId,
		@Schema(description = "문의 접수 시각", example = "2026-11-24T03:00:00Z",
				requiredMode = Schema.RequiredMode.REQUIRED)
		Instant submittedAt) {
}
