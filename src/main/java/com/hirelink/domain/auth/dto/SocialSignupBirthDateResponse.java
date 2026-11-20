package com.hirelink.domain.auth.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record SocialSignupBirthDateResponse(
		@Schema(description = "만 18세 이상 검사 및 저장 완료", requiredMode = Schema.RequiredMode.REQUIRED) boolean ageVerified,
		@Schema(description = "기존 가입 세션 만료 시각. 생년월일 저장으로 연장되지 않음", requiredMode = Schema.RequiredMode.REQUIRED) Instant signupExpiresAt) { }
