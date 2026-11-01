package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class RecommendationScoreCalculator {

	public BigDecimal calculate(Map<RecommendationCriterion, BigDecimal> criterionScores,
			List<RecommendationCriterion> priorities) {
		validateInputs(criterionScores, priorities);
		BigDecimal rawScore = BigDecimal.ZERO;
		for (int index = 0; index < priorities.size(); index++) {
			BigDecimal criterionScore = RecommendationScoreSupport.clamp(criterionScores.get(priorities.get(index)));
			rawScore = rawScore.add(criterionScore.multiply(RecommendationPolicyV1.priorityWeight(index + 1)));
		}
		return RecommendationScoreSupport.clamp(rawScore)
				.setScale(RecommendationPolicyV1.MATCH_SCORE_SCALE, RoundingMode.HALF_UP);
	}

	private void validateInputs(Map<RecommendationCriterion, BigDecimal> scores,
			List<RecommendationCriterion> priorities) {
		if (scores == null || priorities == null || priorities.size() != RecommendationCriterion.values().length
				|| !EnumSet.copyOf(priorities).equals(EnumSet.allOf(RecommendationCriterion.class))) {
			throw new IllegalArgumentException("네 추천 조건의 점수와 우선순위가 모두 필요합니다.");
		}
		for (RecommendationCriterion criterion : RecommendationCriterion.values()) {
			BigDecimal score = scores.get(criterion);
			if (score == null || score.compareTo(RecommendationScoreSupport.ZERO) < 0
					|| score.compareTo(RecommendationScoreSupport.HUNDRED) > 0) {
				throw new IllegalArgumentException("조건별 점수는 0.0부터 100.0 사이여야 합니다.");
			}
		}
	}
}
