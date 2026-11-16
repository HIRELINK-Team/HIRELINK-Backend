package com.hirelink.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CSRF 확인값")
public record CsrfResponse(
		@Schema(description = "재발급·로그아웃 요청에 사용할 CSRF 헤더 이름", example = "X-XSRF-TOKEN", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String headerName,
		@Schema(description = "CSRF 확인값. 쿠키를 유지한 상태에서 headerName 헤더에 그대로 전송",
				example = "csrf-token-example",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String token) {
}
