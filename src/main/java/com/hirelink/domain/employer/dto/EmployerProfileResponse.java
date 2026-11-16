package com.hirelink.domain.employer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사업주 프로필")
public record EmployerProfileResponse(
		@Schema(description = "사업주 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long employerId,
		@Schema(description = "회원 이름", maxLength = 100, example = "김하늘", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String name,
		@Schema(description = "회원 이메일. 소셜 가입 시 제공되지 않으면 null", maxLength = 255, example = "student@example.com", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String email,
		@Schema(description = "국내 010 휴대전화 번호. 입력 시 하이픈 생략 가능, 응답은 하이픈 포함",
				example = "010-1234-5678",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String phoneNumber,
		@Schema(description = "직책. 미등록은 null, 빈 문자열·공백만 입력 불가", maxLength = 50,
				example = "점장",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String position) {
}
