package com.hirelink.domain.recommendation.service;

import java.time.LocalTime;

public record RecommendationTimeRange(String dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
