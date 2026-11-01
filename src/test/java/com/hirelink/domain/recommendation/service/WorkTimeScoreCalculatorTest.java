package com.hirelink.domain.recommendation.service;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkTimeScoreCalculatorTest {

	private final WorkTimeScoreCalculator calculator = new WorkTimeScoreCalculator();

	@Test
	void calculatesFullPartialAndNoOverlap() {
		List<RecommendationTimeRange> required = List.of(time("MON", 9, 0, 13, 0));

		assertThat(calculator.calculate(List.of(time("MON", 8, 0, 14, 0)), required))
				.isEqualByComparingTo("100.0");
		assertThat(calculator.calculate(List.of(time("MON", 11, 0, 15, 0)), required))
				.isEqualByComparingTo("50.0");
		assertThat(calculator.calculate(List.of(time("TUE", 9, 0, 13, 0)), required))
				.isEqualByComparingTo("0.0");
	}

	@Test
	void normalizesMultipleAndOverlappingRangesOnTheSameDay() {
		List<RecommendationTimeRange> required = List.of(
				time("MON", 9, 0, 12, 0),
				time("MON", 11, 0, 14, 0),
				time("MON", 14, 0, 15, 0));
		List<RecommendationTimeRange> available = List.of(
				time("MON", 10, 0, 13, 0),
				time("MON", 12, 0, 14, 0));

		assertThat(calculator.calculate(available, required).doubleValue())
				.isCloseTo(66.6667, org.assertj.core.data.Offset.offset(0.0001));
	}

	private RecommendationTimeRange time(String day, int startHour, int startMinute, int endHour, int endMinute) {
		return new RecommendationTimeRange(day, LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
	}
}
