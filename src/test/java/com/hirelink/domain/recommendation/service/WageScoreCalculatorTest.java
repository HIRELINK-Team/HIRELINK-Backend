package com.hirelink.domain.recommendation.service;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WageScoreCalculatorTest {

	private final WageScoreCalculator calculator = new WageScoreCalculator();
	private final List<RecommendationTimeRange> fortyHours = List.of(
			time("MON"), time("TUE"), time("WED"), time("THU"), time("FRI"));

	@Test
	void calculatesHourlyWageScoreAndCapsItAtOneHundred() {
		assertThat(calculator.calculate("HOURLY", 9_000, 12_000, fortyHours)).isEqualByComparingTo("75.0");
		assertThat(calculator.calculate("HOURLY", 15_000, 12_000, fortyHours)).isEqualByComparingTo("100.0");
	}

	@Test
	void convertsMonthlyWageToHourlyWage() {
		assertThat(calculator.calculate("MONTHLY", 2_086_000, 12_000, fortyHours))
				.isEqualByComparingTo("100.0");
		assertThat(calculator.calculate("MONTHLY", 1_564_200, 12_000, fortyHours).doubleValue())
				.isCloseTo(75.0, org.assertj.core.data.Offset.offset(0.0001));
	}

	@Test
	void returnsOneHundredWhenMinimumHourlyWageIsUnset() {
		assertThat(calculator.calculate("HOURLY", 1, null, fortyHours)).isEqualByComparingTo("100.0");
	}

	private RecommendationTimeRange time(String day) {
		return new RecommendationTimeRange(day, LocalTime.of(9, 0), LocalTime.of(17, 0));
	}
}
