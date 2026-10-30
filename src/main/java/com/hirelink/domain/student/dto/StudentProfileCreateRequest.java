package com.hirelink.domain.student.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudentProfileCreateRequest(
		@NotBlank(message = "이름을 입력해주세요.")
		@Size(max = 100, message = "이름은 100자 이하여야 합니다.") String name,
		@NotNull(message = "생년월일을 입력해주세요.") LocalDate birthDate,
		@NotBlank(message = "국적 코드를 입력해주세요.")
		@Pattern(regexp = "[A-Z]{2}", message = "국적은 영문 대문자 2자리 국가 코드여야 합니다.") String nationality,
		@NotBlank(message = "현재 거주지역을 입력해주세요.")
		@Size(max = 150, message = "현재 거주지역은 150자 이하여야 합니다.") String currentRegion,
		@NotBlank(message = "한국어 수준을 입력해주세요.")
		@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED",
				message = "한국어 수준은 BEGINNER, INTERMEDIATE, ADVANCED 중 하나여야 합니다.") String koreanLevel) {
}
