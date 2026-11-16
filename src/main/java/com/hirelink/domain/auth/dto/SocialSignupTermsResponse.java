package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 가입 약관 목록과 현재 가입 진행 정보에 저장된 동의 상태. 약관 ID 오름차순")
public record SocialSignupTermsResponse(
		@Schema(description = "소셜 가입에서 사용하는 약관. 긴 본문과 번역은 프론트에서 표시한다.",
				requiredMode = Schema.RequiredMode.REQUIRED)
		List<Term> terms) {

	@Schema(name = "SocialSignupTerm")
	public record Term(
			@Schema(description = "약관 종류 ID", example = "4", requiredMode = Schema.RequiredMode.REQUIRED) Long termTypeId,
			@Schema(description = "약관 이름", example = "서비스 이용약관", requiredMode = Schema.RequiredMode.REQUIRED) String termTypeName,
			@Schema(description = "가입 시 반드시 동의해야 하는 약관인지", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean isRequired,
			@Schema(description = "현재 동의 여부. 아직 저장하지 않았다면 false", example = "false", requiredMode = Schema.RequiredMode.REQUIRED) boolean isAgreed,
			@Schema(description = "동의 시각(ISO-8601). 미동의는 null. 같은 동의를 다시 저장하면 기존 시각 유지",
					example = "2026-11-16T10:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant agreedAt) {
	}
}
