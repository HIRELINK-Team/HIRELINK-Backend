package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationReasonGeneratorTest {

	private final RecommendationReasonGenerator generator = new RecommendationReasonGenerator();

	@Test
	void createsOnlySatisfiedReasonsInPriorityOrder() {
		RecommendationPreference preference = preference(false, 12_000, List.of(
				RecommendationCriterion.BUSINESS_TYPE,
				RecommendationCriterion.DISTANCE,
				RecommendationCriterion.WAGE,
				RecommendationCriterion.WORK_TIME));
		Map<RecommendationCriterion, BigDecimal> scores = scores("100", "90", "100", "100");

		assertThat(generator.generate(preference, scores, 0.5)).containsExactly(
				RecommendationPolicyV1.BUSINESS_TYPE_REASON,
				RecommendationPolicyV1.DISTANCE_REASON,
				RecommendationPolicyV1.WORK_TIME_REASON);
	}

	@Test
	void omitsWageAndBusinessReasonsWhenPreferencesAreUnsetOrFlexible() {
		RecommendationPreference preference = preference(true, null, List.of(
				RecommendationCriterion.WAGE,
				RecommendationCriterion.BUSINESS_TYPE,
				RecommendationCriterion.WORK_TIME,
				RecommendationCriterion.DISTANCE));

		assertThat(generator.generate(preference, scores("0", "100", "0", "100"), 5.0)).isEmpty();
	}

	private RecommendationPreference preference(boolean flexible, Integer minimumWage,
			List<RecommendationCriterion> priorities) {
		return new RecommendationPreference(List.of(time()), BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), 1000,
				minimumWage, flexible,
				List.of(new RecommendationPreference.PreferredBusinessType("FOOD_CAFE", null)), priorities);
	}

	private RecommendationTimeRange time() {
		return new RecommendationTimeRange("MON", LocalTime.of(9, 0), LocalTime.of(12, 0));
	}

	private Map<RecommendationCriterion, BigDecimal> scores(String workTime, String wage, String distance,
			String businessType) {
		Map<RecommendationCriterion, BigDecimal> values = new EnumMap<>(RecommendationCriterion.class);
		values.put(RecommendationCriterion.WORK_TIME, new BigDecimal(workTime));
		values.put(RecommendationCriterion.WAGE, new BigDecimal(wage));
		values.put(RecommendationCriterion.DISTANCE, new BigDecimal(distance));
		values.put(RecommendationCriterion.BUSINESS_TYPE, new BigDecimal(businessType));
		return values;
	}
}
