package com.hirelink.domain.auth.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 가입 중 약관 동의 저장. GET /api/auth/social/terms에서 받은 ID를 사용한다.",
		example = "{\"agreements\":[{\"termTypeId\":4,\"isAgreed\":true},{\"termTypeId\":5,\"isAgreed\":true},{\"termTypeId\":6,\"isAgreed\":true},{\"termTypeId\":7,\"isAgreed\":false}]}")
public record SocialSignupTermsRequest(
		@NotEmpty(message = "약관 동의 목록을 입력해주세요.")
		@Valid
		@Schema(description = "필수 약관은 모두 true여야 한다. 생략한 선택 약관은 false로 저장한다. 중복 ID 불가",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<@NotNull(message = "약관 동의 항목은 null일 수 없습니다.") Agreement> agreements) {

	@Schema(name = "SocialSignupTermsRequestAgreement")
	public record Agreement(
			@NotNull(message = "약관 ID를 입력해주세요.")
			@Positive(message = "약관 ID는 양수여야 합니다.")
			@Schema(description = "소셜 가입 약관 조회에서 받은 ID", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
			Long termTypeId,
			@NotNull(message = "약관 동의 여부를 입력해주세요.")
			@Schema(description = "해당 약관 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
			Boolean isAgreed) {
	}
}
