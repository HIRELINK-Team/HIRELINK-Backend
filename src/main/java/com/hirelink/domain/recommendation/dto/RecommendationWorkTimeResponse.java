package com.hirelink.domain.recommendation.dto;

import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 채용공고 근무시간")
public record RecommendationWorkTimeResponse(
		@Schema(description = "요일", example = "MON", allowableValues = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}, requiredMode = Schema.RequiredMode.REQUIRED)
		String dayOfWeek,
		@Schema(description = "근무 시작 시각", example = "09:00:00", type = "string", format = "time", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalTime startTime,
		@Schema(description = "근무 종료 시각", example = "18:00:00", type = "string", format = "time", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalTime endTime) {
}
