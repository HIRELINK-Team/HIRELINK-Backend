package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;
import com.hirelink.domain.recommendation.service.RecommendationPreference.PreferredBusinessType;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationGeneratorTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
	private RecommendationGenerator generator;

	@BeforeEach
	void setUp() {
		generator = new RecommendationGenerator(
				new RecommendationCandidateFilter(),
				new WorkTimeScoreCalculator(),
				new DistanceScoreCalculator(),
				new WageScoreCalculator(),
				new BusinessTypeScoreCalculator(),
				new RecommendationScoreCalculator(),
				new RecommendationReasonGenerator(),
				new RecommendationComparator());
	}

	@Test
	void returnsOnlyTopThreeAfterDeterministicSorting() {
		List<RecommendationCandidate> candidates = List.of(candidate(4L), candidate(2L), candidate(1L), candidate(3L));

		List<RecommendationResult> results = generator.generate(candidates, preference(), TODAY);

		assertThat(results).extracting(RecommendationResult::jobPostingId).containsExactly(1L, 2L, 3L);
		assertThat(results).allSatisfy(result -> {
			assertThat(result.matchScore()).isEqualByComparingTo("100.0");
			assertThat(result.reasons()).containsExactly(
					RecommendationPolicyV1.WORK_TIME_REASON,
					RecommendationPolicyV1.WAGE_REASON,
					RecommendationPolicyV1.DISTANCE_REASON,
					RecommendationPolicyV1.BUSINESS_TYPE_REASON);
		});
	}

	@Test
	void returnsEmptyResultWhenNoCandidatePassesHardFilter() {
		RecommendationCandidate closed = new RecommendationCandidate(1L, JobPostingStatus.CLOSED, TODAY, 0, false,
				latitude(), longitude(), "HOURLY", 13_000, "FOOD_CAFE", null, List.of(time()));

		assertThat(generator.generate(List.of(closed), preference(), TODAY)).isEmpty();
		assertThat(generator.generate(List.of(), preference(), TODAY)).isEmpty();
	}

	private RecommendationPreference preference() {
		return new RecommendationPreference(
				List.of(time()), latitude(), longitude(), 1000, 12_000, false,
				List.of(new PreferredBusinessType("FOOD_CAFE", null)),
				List.of(
						RecommendationCriterion.WORK_TIME,
						RecommendationCriterion.WAGE,
						RecommendationCriterion.DISTANCE,
						RecommendationCriterion.BUSINESS_TYPE));
	}

	private RecommendationCandidate candidate(long id) {
		return new RecommendationCandidate(id, JobPostingStatus.RECRUITING, TODAY, 0, false,
				latitude(), longitude(), "HOURLY", 13_000, "FOOD_CAFE", null, List.of(time()));
	}

	private RecommendationTimeRange time() {
		return new RecommendationTimeRange("MON", LocalTime.of(9, 0), LocalTime.of(12, 0));
	}

	private BigDecimal latitude() {
		return BigDecimal.valueOf(37.5);
	}

	private BigDecimal longitude() {
		return BigDecimal.valueOf(127);
	}
}
