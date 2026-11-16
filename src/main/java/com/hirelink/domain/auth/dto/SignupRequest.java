package com.hirelink.domain.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "LOCAL 이메일 회원가입. 이메일·비밀번호·회원 유형 필수.",
		example = "{\"email\":\"student@example.com\",\"password\":\"Example123!\",\"role\":\"STUDENT\"}")
public record SignupRequest(
		@NotBlank(message = "이메일은 필수입니다.")
		@Email(message = "올바른 이메일 형식이 아닙니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		@Schema(description = "이메일. 요청에서는 앞뒤 공백 제거 후 소문자로 정규화", maxLength = 255,
				example = "student@example.com",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String email,

		@NotBlank(message = "비밀번호는 필수입니다.")
		@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
		@Pattern(regexp = "(?s)^(?=.*[a-zA-Z])(?=.*[0-9]).*$", message = "비밀번호는 영문과 숫자를 포함해야 합니다.")
		@Schema(description = "비밀번호. 8자 이상, 영문과 숫자 포함",
				example = "Example123!", minLength = 8,
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String password,

		@NotBlank(message = "역할은 필수입니다.")
		@Pattern(regexp = "STUDENT|EMPLOYER", message = "역할은 STUDENT 또는 EMPLOYER여야 합니다.")
		@Schema(description = "회원 유형",
				allowableValues = {"STUDENT", "EMPLOYER"},
				example = "STUDENT",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String role
) {
	public SignupRequest {
		// 검증과 중복 조회가 동일한 정규화된 이메일을 사용하도록 한다.
		if (email != null) {
			email = email.trim().toLowerCase(Locale.ROOT);
		}
	}
}
