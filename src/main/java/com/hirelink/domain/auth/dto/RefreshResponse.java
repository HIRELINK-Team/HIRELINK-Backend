package com.hirelink.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access Token 재발급 결과")
public record RefreshResponse(
		@Schema(description = "Access Token. 보호된 API의 Authorization: Bearer 헤더에 사용",
				example = "access-token-example",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String accessToken,
		@Schema(description = "인증 토큰 유형", allowableValues = {"Bearer"}, example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String tokenType,
		@Schema(description = "유효시간 (초). 최대 3600초이며 해당 로그인 기록의 만료 시각을 넘지 않는다", example = "3600", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		long expiresIn) {
}
