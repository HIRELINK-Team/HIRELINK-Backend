package com.hirelink.domain.auth.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "휴대전화 인증 완료 결과")
public record PhoneVerificationConfirmResponse(
		@Schema(description = "국내 010 휴대전화 번호. 입력 시 하이픈 생략 가능, 응답은 하이픈 포함",
				example = "010-1234-5678",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String phoneNumber,
		@Schema(description = "휴대전화 인증 완료 시각 (ISO-8601)",
				example = "2026-11-01T01:00:00Z", type = "string", format = "date-time",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Instant verifiedAt,
		@Schema(description = "처리 결과 메시지", example = "처리가 완료되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String message) {
}
