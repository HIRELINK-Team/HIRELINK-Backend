package com.hirelink.domain.recommendation.dto;

import java.time.LocalTime;

public record RecommendationWorkTimeResponse(
		String dayOfWeek,
		LocalTime startTime,
		LocalTime endTime) {
}
