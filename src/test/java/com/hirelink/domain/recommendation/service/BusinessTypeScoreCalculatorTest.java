package com.hirelink.domain.recommendation.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hirelink.domain.recommendation.service.RecommendationPreference.PreferredBusinessType;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTypeScoreCalculatorTest {

	private final BusinessTypeScoreCalculator calculator = new BusinessTypeScoreCalculator();

	@Test
	void scoresMatchingAndMismatchingBusinessTypes() {
		List<PreferredBusinessType> preferred = List.of(new PreferredBusinessType("FOOD_CAFE", null));

		assertThat(calculator.calculate(false, preferred, "FOOD_CAFE", null)).isEqualByComparingTo("100.0");
		assertThat(calculator.calculate(false, preferred, "RETAIL", null)).isEqualByComparingTo("0.0");
	}

	@Test
	void flexiblePreferenceAlwaysScoresOneHundred() {
		assertThat(calculator.calculate(true, List.of(), "RETAIL", null)).isEqualByComparingTo("100.0");
	}

	@Test
	void normalizesOtherBusinessTypeNameBeforeComparing() {
		List<PreferredBusinessType> preferred = List.of(new PreferredBusinessType("OTHER", " 영화 관 "));

		assertThat(calculator.calculate(false, preferred, "OTHER", "영화관")).isEqualByComparingTo("100.0");
		assertThat(calculator.calculate(false, preferred, "OTHER", "공연장")).isEqualByComparingTo("0.0");
	}
}
