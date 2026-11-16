package com.hirelink.domain.auth.dto;

import com.hirelink.domain.member.entity.MemberRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이메일 회원가입 결과")
public record SignupResponse(
		@Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long userId,
		@Schema(description = "회원 이메일", maxLength = 255, example = "student@example.com", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String email,
		@Schema(description = "회원 유형",
				allowableValues = {"STUDENT", "EMPLOYER"},
				example = "STUDENT",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		MemberRole role,
		@Schema(description = "Access Token. 보호된 API의 Authorization: Bearer 헤더에 사용",
				example = "access-token-example",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String accessToken,
		@Schema(description = "인증 토큰 유형", allowableValues = {"Bearer"}, example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String tokenType,
		@Schema(description = "유효시간 (초)", example = "3600", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		long expiresIn) {
}
