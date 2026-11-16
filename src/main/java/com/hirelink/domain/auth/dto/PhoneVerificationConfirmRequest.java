package com.hirelink.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "휴대전화 인증번호 확인. 요청했던 전화번호와 문자 인증번호를 함께 입력한다.")
public record PhoneVerificationConfirmRequest(
		@NotBlank(message = "휴대전화 번호를 입력해주세요.")
		@Pattern(regexp = "010(?:[0-9]{8}|-[0-9]{4}-[0-9]{4})", message = "010으로 시작하는 휴대전화 번호를 입력해주세요.")
		@Schema(description = "국내 010 휴대전화 번호. 입력 시 하이픈 생략 가능, 응답은 하이픈 포함",
				example = "010-1234-5678",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String phoneNumber,
		@NotBlank(message = "인증번호를 입력해주세요.")
		@Pattern(regexp = "[0-9]{6}", message = "6자리 인증번호를 입력해주세요.")
		@Schema(description = "문자로 전송된 6자리 인증번호. 앞자리 0을 유지하는 문자열",
				pattern = "[0-9]{6}",
				example = "012345",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String code) {

	public String normalizedPhoneNumber() {
		return phoneNumber.replace("-", "");
	}
}
