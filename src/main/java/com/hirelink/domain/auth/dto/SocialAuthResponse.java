package com.hirelink.domain.auth.dto;

import java.time.Instant;

import com.hirelink.domain.member.entity.MemberRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 인증 결과. 신규 사용자는 가입 진행 정보만 받으며 아직 회원이 아니다")
public record SocialAuthResponse(
		@Schema(description = "LOGIN: 기존 회원 로그인, SIGNUP_REQUIRED: 약관·전화번호 인증 필요",
				allowableValues = {"LOGIN", "SIGNUP_REQUIRED"}, requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Status status,
		@Schema(description = "회원 유형", allowableValues = {"STUDENT", "EMPLOYER"},
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		MemberRole role,
		@Schema(description = "소셜 제공자가 확인한 이메일. 제공되지 않으면 null. 계정 연결에 사용하지 않음",
				example = "member@example.com", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String email,
		@Schema(description = "가입 진행 정보 만료 시각(UTC). 신규 사용자만 반환, 기존 회원은 null",
				example = "2026-11-16T10:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Instant signupExpiresAt,
		@Schema(description = "기존 회원 로그인 결과. 신규 사용자는 null이며 Access Token을 발급하지 않음",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		LoginResponse login) {

	public enum Status { LOGIN, SIGNUP_REQUIRED }
}
