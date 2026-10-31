package com.hirelink.domain.application.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ApplicationCreateRequest(
		@NotNull(message = "추천을 선택해주세요.")
		@Positive(message = "추천 ID는 양수여야 합니다.") Long recommendationId,
		@NotNull(message = "희망 근무 시작일을 입력해주세요.") LocalDate desiredStartDate) {
}
