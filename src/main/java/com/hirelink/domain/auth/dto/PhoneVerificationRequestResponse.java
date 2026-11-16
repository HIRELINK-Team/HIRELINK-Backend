package com.hirelink.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증번호 발송 결과")
public record PhoneVerificationRequestResponse(
		@Schema(description = "인증번호 유효시간 (초), 300초", example = "300", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		long expiresIn,
		@Schema(description = "처리 결과 메시지", example = "처리가 완료되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String message) {
}
