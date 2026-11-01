package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationPreference(
		List<RecommendationTimeRange> availableWorkTimes,
		BigDecimal latitude,
		BigDecimal longitude,
		Integer radiusM,
		Integer minimumHourlyWage,
		boolean businessTypeFlexible,
		List<PreferredBusinessType> preferredBusinessTypes,
		List<RecommendationCriterion> priorities) {

	public RecommendationPreference {
		availableWorkTimes = availableWorkTimes == null ? List.of() : List.copyOf(availableWorkTimes);
		preferredBusinessTypes = preferredBusinessTypes == null ? List.of() : List.copyOf(preferredBusinessTypes);
		priorities = priorities == null ? List.of() : List.copyOf(priorities);
	}

	public record PreferredBusinessType(String businessTypeName, String otherJobName) {
	}
}
