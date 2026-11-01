package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.hirelink.domain.recommendation.service.DistanceScoreCalculator.DistanceScore;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceScoreCalculatorTest {

	private final DistanceScoreCalculator calculator = new DistanceScoreCalculator();

	@Test
	void returnsOneHundredInsidePreferredRadius() {
		DistanceScore result = calculate(37.5000, 127.0000, 2000, 37.5100, 127.0000);

		assertThat(result.distanceKm()).isLessThan(2.0);
		assertThat(result.score()).isEqualByComparingTo("100.0");
	}

	@Test
	void linearlyDecreasesOutsideRadius() {
		DistanceScore result = calculate(37.5000, 127.0000, 1000, 37.5180, 127.0000);

		assertThat(result.distanceKm()).isBetween(1.9, 2.1);
		assertThat(result.score().doubleValue()).isBetween(63.0, 70.0);
	}

	@Test
	void returnsZeroAtLeastThreeKilometersOutsideRadius() {
		DistanceScore result = calculate(37.5000, 127.0000, 1000, 37.5400, 127.0000);

		assertThat(result.distanceKm()).isGreaterThan(4.0);
		assertThat(result.score()).isEqualByComparingTo("0.0");
	}

	private DistanceScore calculate(double studentLatitude, double studentLongitude, int radiusM,
			double workplaceLatitude, double workplaceLongitude) {
		return calculator.calculate(BigDecimal.valueOf(studentLatitude), BigDecimal.valueOf(studentLongitude), radiusM,
				BigDecimal.valueOf(workplaceLatitude), BigDecimal.valueOf(workplaceLongitude));
	}
}
