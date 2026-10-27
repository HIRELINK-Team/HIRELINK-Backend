package com.hirelink.domain.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank(message = "이메일은 필수입니다.")
		@Email(message = "올바른 이메일 형식이 아닙니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		@NotBlank(message = "비밀번호는 필수입니다.")
		@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
		@Pattern(regexp = "(?s)^(?=.*[a-zA-Z])(?=.*[0-9]).*$", message = "비밀번호는 영문과 숫자를 포함해야 합니다.")
		String password,

		@NotBlank(message = "역할은 필수입니다.")
		@Pattern(regexp = "STUDENT|EMPLOYER", message = "역할은 STUDENT 또는 EMPLOYER여야 합니다.")
		String role
) {
	public SignupRequest {
		// 검증과 중복 조회가 동일한 정규화된 이메일을 사용하도록 한다.
		if (email != null) {
			email = email.trim().toLowerCase(Locale.ROOT);
		}
	}
}
