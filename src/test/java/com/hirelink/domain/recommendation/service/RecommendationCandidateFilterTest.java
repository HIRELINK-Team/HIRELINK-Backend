package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationCandidateFilterTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
	private final RecommendationCandidateFilter filter = new RecommendationCandidateFilter();

	@Test
	void acceptsRecruitingPostingAtBoundaryValues() {
		RecommendationCandidate candidate = candidate(JobPostingStatus.RECRUITING, TODAY, 4, false,
				BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), "HOURLY", 1, List.of(validTime()));

		assertThat(filter.isEligible(candidate, TODAY)).isTrue();
	}

	@Test
	void rejectsClosedExpiredFullOrPreviouslyAppliedPosting() {
		assertThat(filter.isEligible(candidate(JobPostingStatus.CLOSED, TODAY, 4, false,
				latitude(), longitude(), "HOURLY", 1, List.of(validTime())), TODAY)).isFalse();
		assertThat(filter.isEligible(candidate(JobPostingStatus.RECRUITING, TODAY.minusDays(1), 4, false,
				latitude(), longitude(), "HOURLY", 1, List.of(validTime())), TODAY)).isFalse();
		assertThat(filter.isEligible(candidate(JobPostingStatus.RECRUITING, TODAY, 5, false,
				latitude(), longitude(), "HOURLY", 1, List.of(validTime())), TODAY)).isFalse();
		assertThat(filter.isEligible(candidate(JobPostingStatus.RECRUITING, TODAY, 4, true,
				latitude(), longitude(), "HOURLY", 1, List.of(validTime())), TODAY)).isFalse();
	}

	@Test
	void rejectsInvalidCoordinatesWorkTimesOrWageData() {
		assertThat(filter.isEligible(candidate(JobPostingStatus.RECRUITING, TODAY, 0, false,
				BigDecimal.valueOf(91), longitude(), "HOURLY", 1, List.of(validTime())), TODAY)).isFalse();
		assertThat(filter.isEligible(candidate(JobPostingStatus.RECRUITING, TODAY, 0, false,
				latitude(), longitude(), "HOURLY", 1, List.of()), TODAY)).isFalse();
		assertThat(filter.isEligible(candidate(JobPostingStatus.RECRUITING, TODAY, 0, false,
				latitude(), longitude(), "DAILY", 1, List.of(validTime())), TODAY)).isFalse();
		assertThat(filter.isEligible(candidate(JobPostingStatus.RECRUITING, TODAY, 0, false,
				latitude(), longitude(), "MONTHLY", 0, List.of(validTime())), TODAY)).isFalse();
	}

	private RecommendationCandidate candidate(JobPostingStatus status, LocalDate deadline, long activeCount,
			boolean previouslyApplied, BigDecimal latitude, BigDecimal longitude, String wageType, Integer wageAmount,
			List<RecommendationTimeRange> workTimes) {
		return new RecommendationCandidate(1L, status, deadline, activeCount, previouslyApplied, latitude, longitude,
				wageType, wageAmount, "FOOD_CAFE", null, workTimes);
	}

	private RecommendationTimeRange validTime() {
		return new RecommendationTimeRange("MON", LocalTime.of(9, 0), LocalTime.of(12, 0));
	}

	private BigDecimal latitude() {
		return BigDecimal.valueOf(37.5);
	}

	private BigDecimal longitude() {
		return BigDecimal.valueOf(127);
	}
}
