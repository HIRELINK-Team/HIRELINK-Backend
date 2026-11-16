package com.hirelink.domain.auth.dto;

import com.hirelink.domain.member.entity.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 계정 생성 결과. 온보딩 완료를 의미하지 않는다. 필수 온보딩 단계 조회·재개 판정은 후속 기능")
public record SocialSignupResponse(
		@Schema(description = "생성한 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long userId,
		@Schema(description = "소셜 인증 전에 선택한 회원 유형", allowableValues = {"STUDENT", "EMPLOYER"}, requiredMode = Schema.RequiredMode.REQUIRED) MemberRole role,
		@Schema(description = "보호된 API에 사용할 Access Token", example = "access-token-example", requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
		@Schema(description = "토큰 유형", allowableValues = {"Bearer"}, requiredMode = Schema.RequiredMode.REQUIRED) String tokenType,
		@Schema(description = "Access Token 유효시간(초)", example = "3600", requiredMode = Schema.RequiredMode.REQUIRED) long expiresIn,
		@Schema(description = "새 계정은 필수 온보딩이 필요하므로 true. 메인 화면으로 바로 이동하지 않는다", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean onboardingRequired) {
}
