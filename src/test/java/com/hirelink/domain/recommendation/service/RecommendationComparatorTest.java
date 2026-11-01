package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationComparatorTest {

	private final RecommendationComparator comparator = new RecommendationComparator();
	private final List<RecommendationCriterion> priorities = List.of(
			RecommendationCriterion.WORK_TIME,
			RecommendationCriterion.WAGE,
			RecommendationCriterion.DISTANCE,
			RecommendationCriterion.BUSINESS_TYPE);

	@Test
	void sortsByMatchScoreThenPriorityScoresDistanceAndId() {
		List<RecommendationResult> results = new ArrayList<>(List.of(
				result(6, "90", "100", "50", "50", "50", 2.0),
				result(5, "90", "100", "60", "40", "50", 2.0),
				result(4, "90", "100", "60", "50", "40", 2.0),
				result(3, "90", "100", "60", "50", "50", 3.0),
				result(2, "90", "100", "60", "50", "50", 2.0),
				result(1, "91", "0", "0", "0", "0", 10.0)));

		results.sort(comparator.forPriorities(priorities));

		assertThat(results).extracting(RecommendationResult::jobPostingId)
				.containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
	}

	@Test
	void usesJobPostingIdAsTheFinalDeterministicTieBreaker() {
		List<RecommendationResult> results = new ArrayList<>(List.of(
				result(20, "80", "80", "80", "80", "80", 1.0),
				result(10, "80", "80", "80", "80", "80", 1.0)));

		results.sort(comparator.forPriorities(priorities));

		assertThat(results).extracting(RecommendationResult::jobPostingId).containsExactly(10L, 20L);
	}

	private RecommendationResult result(long id, String match, String work, String wage, String distance,
			String business, double distanceKm) {
		Map<RecommendationCriterion, BigDecimal> scores = new EnumMap<>(RecommendationCriterion.class);
		scores.put(RecommendationCriterion.WORK_TIME, new BigDecimal(work));
		scores.put(RecommendationCriterion.WAGE, new BigDecimal(wage));
		scores.put(RecommendationCriterion.DISTANCE, new BigDecimal(distance));
		scores.put(RecommendationCriterion.BUSINESS_TYPE, new BigDecimal(business));
		return new RecommendationResult(id, new BigDecimal(match), scores, distanceKm, List.of());
	}
}
