package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScoreCalculatorTest {

	private final RecommendationScoreCalculator calculator = new RecommendationScoreCalculator();
	private final List<RecommendationCriterion> priorities = List.of(
			RecommendationCriterion.WORK_TIME,
			RecommendationCriterion.WAGE,
			RecommendationCriterion.DISTANCE,
			RecommendationCriterion.BUSINESS_TYPE);

	@Test
	void appliesWeightsByPriorityOrder() {
		Map<RecommendationCriterion, BigDecimal> scores = scores("100", "80", "60", "40");

		assertThat(calculator.calculate(scores, priorities)).isEqualByComparingTo("80.0");
	}

	@Test
	void roundsFinalScoreToOneDecimalWithHalfUp() {
		Map<RecommendationCriterion, BigDecimal> scores = scores("25.125", "0", "0", "0");

		assertThat(calculator.calculate(scores, priorities)).isEqualByComparingTo("10.1");
	}

	private Map<RecommendationCriterion, BigDecimal> scores(String workTime, String wage, String distance,
			String businessType) {
		Map<RecommendationCriterion, BigDecimal> scores = new EnumMap<>(RecommendationCriterion.class);
		scores.put(RecommendationCriterion.WORK_TIME, new BigDecimal(workTime));
		scores.put(RecommendationCriterion.WAGE, new BigDecimal(wage));
		scores.put(RecommendationCriterion.DISTANCE, new BigDecimal(distance));
		scores.put(RecommendationCriterion.BUSINESS_TYPE, new BigDecimal(businessType));
		return scores;
	}
}
