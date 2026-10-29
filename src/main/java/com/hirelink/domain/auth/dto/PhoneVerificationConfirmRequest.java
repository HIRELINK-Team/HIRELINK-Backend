package com.hirelink.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerificationConfirmRequest(
		@NotBlank(message = "휴대전화 번호를 입력해주세요.")
		@Pattern(regexp = "010(?:[0-9]{8}|-[0-9]{4}-[0-9]{4})", message = "010으로 시작하는 휴대전화 번호를 입력해주세요.")
		String phoneNumber,
		@NotBlank(message = "인증번호를 입력해주세요.")
		@Pattern(regexp = "[0-9]{6}", message = "6자리 인증번호를 입력해주세요.")
		String code) {

	public String normalizedPhoneNumber() {
		return phoneNumber.replace("-", "");
	}
}
