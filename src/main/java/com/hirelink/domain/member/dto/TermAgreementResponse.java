package com.hirelink.domain.member.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장한 약관 동의 상태")
public record TermAgreementResponse(
		@Schema(description = "이번 요청에서 저장한 약관 목록. 요청 순서로 반환", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<Agreement> agreements) {

	@Schema(name = "TermAgreementResponseAgreement")
	public record Agreement(
			@Schema(description = "약관 종류 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long termTypeId,
			@Schema(description = "해당 약관 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			boolean isAgreed,
			@Schema(description = "동의 시각 (ISO-8601). false이면 null, true를 반복 전송하면 기존 동의 시각 유지",
					example = "2026-11-01T01:00:00Z", type = "string", format = "date-time",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
			Instant agreedAt) {
	}
}
