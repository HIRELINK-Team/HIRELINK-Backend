package com.hirelink.domain.jobposting.dto;

import java.time.Instant;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용공고 모집 마감 결과")
public record JobPostingCloseResponse(
		@Schema(description = "채용공고 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long jobPostingId,
		@Schema(description = "공고 상태", allowableValues = {"CLOSED"}, example = "CLOSED", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		JobPostingStatus status,
		@Schema(description = "모집 마감 시각 (ISO-8601)",
				example = "2026-11-01T01:00:00Z", type = "string", format = "date-time",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Instant closedAt) {
}
