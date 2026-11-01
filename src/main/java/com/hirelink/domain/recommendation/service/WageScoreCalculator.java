package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class WageScoreCalculator {

	public BigDecimal calculate(String wageType, Integer wageAmount, Integer minimumHourlyWage,
			List<RecommendationTimeRange> requiredWorkTimes) {
		if (!isValidWage(wageType, wageAmount, requiredWorkTimes)) {
			throw new IllegalArgumentException("급여 계산 데이터가 유효하지 않습니다.");
		}
		if (minimumHourlyWage == null) {
			return RecommendationScoreSupport.HUNDRED;
		}
		if (minimumHourlyWage <= 0) {
			throw new IllegalArgumentException("최소 희망 시급은 1원 이상이어야 합니다.");
		}
		BigDecimal hourlyWage = hourlyWage(wageType, wageAmount, requiredWorkTimes);
		BigDecimal ratio = hourlyWage.divide(BigDecimal.valueOf(minimumHourlyWage), 10, RoundingMode.HALF_UP);
		return RecommendationScoreSupport.clamp(ratio.multiply(RecommendationScoreSupport.HUNDRED));
	}

	static boolean isValidWage(String wageType, Integer wageAmount, List<RecommendationTimeRange> requiredWorkTimes) {
		if (wageAmount == null || wageAmount <= 0 || !("HOURLY".equals(wageType) || "MONTHLY".equals(wageType))) {
			return false;
		}
		return !"MONTHLY".equals(wageType) || WorkTimeNormalizer.allValid(requiredWorkTimes);
	}

	private BigDecimal hourlyWage(String wageType, Integer wageAmount,
			List<RecommendationTimeRange> requiredWorkTimes) {
		if ("HOURLY".equals(wageType)) {
			return BigDecimal.valueOf(wageAmount);
		}
		long weeklyMinutes = WorkTimeNormalizer.totalMinutes(WorkTimeNormalizer.normalize(requiredWorkTimes));
		BigDecimal monthlyHours = BigDecimal.valueOf(weeklyMinutes)
				.divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
				.multiply(RecommendationPolicyV1.WEEKS_PER_MONTH);
		return BigDecimal.valueOf(wageAmount).divide(monthlyHours, 10, RoundingMode.HALF_UP);
	}
}
