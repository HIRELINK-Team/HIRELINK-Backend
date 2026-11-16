package com.hirelink.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "휴대전화 인증번호 요청. 국내 010 번호만 지원한다.")
public record PhoneVerificationRequest(
		@NotBlank(message = "휴대전화 번호를 입력해주세요.")
		@Pattern(regexp = "010(?:[0-9]{8}|-[0-9]{4}-[0-9]{4})", message = "010으로 시작하는 휴대전화 번호를 입력해주세요.")
		@Schema(description = "국내 010 휴대전화 번호. 입력 시 하이픈 생략 가능, 응답은 하이픈 포함",
				example = "010-1234-5678",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String phoneNumber) {

	public String normalizedPhoneNumber() {
		return phoneNumber.replace("-", "");
	}
}
