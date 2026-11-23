package com.hirelink.domain.member.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 회원의 약관별 동의 상태 저장. userId는 요청에 포함하지 않는다.",
		example = "{\"agreements\":[{\"termTypeId\":1,\"version\":\"v1\",\"isAgreed\":true},{\"termTypeId\":3,\"version\":\"v1\",\"isAgreed\":false}]}")
public record TermAgreementRequest(
		@NotNull(message = "약관 동의 목록을 입력해주세요.")
		@Valid @Schema(description = "이번 요청에서 저장할 약관 목록. 빈 목록 허용, 같은 약관 ID 중복 불가", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<@NotNull(message = "약관 동의 항목은 null일 수 없습니다.") Agreement> agreements) {

	@Schema(name = "TermAgreementRequestAgreement")
	public record Agreement(
			@NotNull(message = "약관 ID를 입력해주세요.")
			@Positive(message = "약관 ID는 양수여야 합니다.")
			@Schema(description = "약관 종류 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long termTypeId,
			@NotNull(message = "약관 동의 여부를 입력해주세요.")
			@Schema(description = "해당 약관 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Boolean isAgreed,
			@NotBlank(message = "표시한 약관 버전을 입력해주세요.")
			@Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", message = "약관 버전 형식이 올바르지 않습니다.")
			@Schema(description = "프론트가 실제 표시한 본문의 버전. 현재 버전과 정확히 일치해야 하며 서버가 자동으로 채우지 않는다", example = "v1", requiredMode = Schema.RequiredMode.REQUIRED)
			String version) {
	}
}
