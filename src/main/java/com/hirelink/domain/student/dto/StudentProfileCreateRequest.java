package com.hirelink.domain.student.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "휴대전화 인증 후 최초 유학생 프로필 등록.")
public record StudentProfileCreateRequest(
		@NotBlank(message = "이름을 입력해주세요.")
		@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
		@Schema(description = "회원 이름", maxLength = 100, example = "김하늘", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String name,
		@NotNull(message = "생년월일을 입력해주세요.")
		@Schema(description = "생년월일 (YYYY-MM-DD). 한국 기준 만 18세 이상", example = "2000-05-14", type = "string", format = "date", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		LocalDate birthDate,
		@NotBlank(message = "국적 코드를 입력해주세요.")
		@Pattern(regexp = "[A-Z]{2}", message = "국적은 영문 대문자 2자리 국가 코드여야 합니다.")
		@Schema(description = "ISO 3166-1 alpha-2 국가 코드 (영문 대문자 2자리)", pattern = "[A-Z]{2}", example = "VN", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String nationality,
		@NotBlank(message = "현재 거주지역을 입력해주세요.")
		@Size(max = 150, message = "현재 거주지역은 150자 이하여야 합니다.")
		@Schema(description = "현재 거주지역. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "서울특별시 성동구", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String currentRegion,
		@NotBlank(message = "한국어 수준을 입력해주세요.")
		@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED",
				message = "한국어 수준은 BEGINNER, INTERMEDIATE, ADVANCED 중 하나여야 합니다.") @Schema(description = "한국어 수준. 화면에서는 초급·중급·고급으로 변환 가능", allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"}, example = "INTERMEDIATE", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String koreanLevel) {
}
