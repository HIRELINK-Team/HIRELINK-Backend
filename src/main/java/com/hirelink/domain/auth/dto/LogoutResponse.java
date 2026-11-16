package com.hirelink.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그아웃 결과")
public record LogoutResponse(
		@Schema(description = "처리 결과 메시지", example = "처리가 완료되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String message) {
}
