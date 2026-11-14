package com.hirelink.domain.application.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원 생성 요청")
public record ApplicationCreateRequest(
		@Schema(description = "본인 최신 추천 배치의 추천 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "추천을 선택해주세요.")
		@Positive(message = "추천 ID는 양수여야 합니다.") Long recommendationId,
		@Schema(description = "희망 근무 시작일. 요청일 이후이며 공고 근무 시작일보다 빠를 수 없습니다.", example = "2026-12-01", type = "string", format = "date", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "희망 근무 시작일을 입력해주세요.") LocalDate desiredStartDate) {
}
