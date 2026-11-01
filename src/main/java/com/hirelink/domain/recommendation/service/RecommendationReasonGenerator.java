package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class RecommendationReasonGenerator {

	public List<String> generate(RecommendationPreference preference,
			Map<RecommendationCriterion, BigDecimal> criterionScores, double distanceKm) {
		List<String> reasons = new ArrayList<>();
		for (RecommendationCriterion criterion : preference.priorities()) {
			String reason = reasonFor(criterion, preference, criterionScores, distanceKm);
			if (reason != null) {
				reasons.add(reason);
			}
		}
		return List.copyOf(reasons);
	}

	private String reasonFor(RecommendationCriterion criterion, RecommendationPreference preference,
			Map<RecommendationCriterion, BigDecimal> scores, double distanceKm) {
		return switch (criterion) {
			case WORK_TIME -> isPerfect(scores.get(criterion)) ? RecommendationPolicyV1.WORK_TIME_REASON : null;
			case WAGE -> preference.minimumHourlyWage() != null && isPerfect(scores.get(criterion))
					? RecommendationPolicyV1.WAGE_REASON : null;
			case DISTANCE -> distanceKm <= preference.radiusM() / 1000.0
					? RecommendationPolicyV1.DISTANCE_REASON : null;
			case BUSINESS_TYPE -> !preference.businessTypeFlexible() && isPerfect(scores.get(criterion))
					? RecommendationPolicyV1.BUSINESS_TYPE_REASON : null;
		};
	}

	private boolean isPerfect(BigDecimal score) {
		return score.compareTo(RecommendationScoreSupport.HUNDRED) == 0;
	}
}
