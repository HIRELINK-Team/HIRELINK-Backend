package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;

final class RecommendationScoreSupport {

	static final BigDecimal ZERO = new BigDecimal("0.0");
	static final BigDecimal HUNDRED = new BigDecimal("100.0");

	private RecommendationScoreSupport() {
	}

	static BigDecimal clamp(BigDecimal score) {
		if (score.compareTo(ZERO) < 0) {
			return ZERO;
		}
		if (score.compareTo(HUNDRED) > 0) {
			return HUNDRED;
		}
		return score;
	}
}
