package com.hirelink.domain.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "LOCAL 이메일 로그인. 가입 때의 이메일과 비밀번호를 입력한다.", example = "{\"email\":\"student@example.com\",\"password\":\"Example123!\"}")
public record LoginRequest(
		@NotBlank(message = "이메일은 필수입니다.")
		@Email(message = "올바른 이메일 형식이 아닙니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		@Schema(description = "이메일. 요청에서는 앞뒤 공백 제거 후 소문자로 정규화", maxLength = 255,
				example = "student@example.com",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String email,
		@NotBlank(message = "비밀번호는 필수입니다.")
		@Schema(description = "비밀번호", example = "Example123!", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String password
) {
	public LoginRequest {
		if (email != null) {
			email = email.trim().toLowerCase(Locale.ROOT);
		}
	}
}
