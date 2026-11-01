package com.hirelink.domain.recommendation.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class RecommendationComparator {

	public Comparator<RecommendationResult> forPriorities(List<RecommendationCriterion> priorities) {
		if (priorities == null || priorities.size() != RecommendationCriterion.values().length) {
			throw new IllegalArgumentException("네 추천 조건의 우선순위가 모두 필요합니다.");
		}
		Comparator<RecommendationResult> comparator = Comparator.comparing(RecommendationResult::matchScore).reversed();
		for (RecommendationCriterion priority : priorities) {
			comparator = comparator.thenComparing(
					(result) -> result.criterionScores().get(priority), Comparator.reverseOrder());
		}
		return comparator.thenComparingDouble(RecommendationResult::distanceKm)
				.thenComparing(RecommendationResult::jobPostingId);
	}
}
