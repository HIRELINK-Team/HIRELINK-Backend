package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.hirelink.domain.recommendation.service.DistanceScoreCalculator.DistanceScore;

@Component
public class RecommendationGenerator {

	private final RecommendationCandidateFilter candidateFilter;
	private final WorkTimeScoreCalculator workTimeScoreCalculator;
	private final DistanceScoreCalculator distanceScoreCalculator;
	private final WageScoreCalculator wageScoreCalculator;
	private final BusinessTypeScoreCalculator businessTypeScoreCalculator;
	private final RecommendationScoreCalculator scoreCalculator;
	private final RecommendationReasonGenerator reasonGenerator;
	private final RecommendationComparator recommendationComparator;

	public RecommendationGenerator(RecommendationCandidateFilter candidateFilter,
			WorkTimeScoreCalculator workTimeScoreCalculator, DistanceScoreCalculator distanceScoreCalculator,
			WageScoreCalculator wageScoreCalculator, BusinessTypeScoreCalculator businessTypeScoreCalculator,
			RecommendationScoreCalculator scoreCalculator, RecommendationReasonGenerator reasonGenerator,
			RecommendationComparator recommendationComparator) {
		this.candidateFilter = candidateFilter;
		this.workTimeScoreCalculator = workTimeScoreCalculator;
		this.distanceScoreCalculator = distanceScoreCalculator;
		this.wageScoreCalculator = wageScoreCalculator;
		this.businessTypeScoreCalculator = businessTypeScoreCalculator;
		this.scoreCalculator = scoreCalculator;
		this.reasonGenerator = reasonGenerator;
		this.recommendationComparator = recommendationComparator;
	}

	public List<RecommendationResult> generate(List<RecommendationCandidate> candidates,
			RecommendationPreference preference, LocalDate evaluationDate) {
		List<RecommendationResult> results = new ArrayList<>();
		for (RecommendationCandidate candidate : candidates) {
			if (!candidateFilter.isEligible(candidate, evaluationDate)) {
				continue;
			}
			results.add(calculate(candidate, preference));
		}
		return results.stream()
				.sorted(recommendationComparator.forPriorities(preference.priorities()))
				.limit(RecommendationPolicyV1.MAX_RECOMMENDATIONS)
				.toList();
	}

	private RecommendationResult calculate(RecommendationCandidate candidate, RecommendationPreference preference) {
		DistanceScore distanceScore = distanceScoreCalculator.calculate(preference.latitude(), preference.longitude(),
				preference.radiusM(), candidate.workplaceLatitude(), candidate.workplaceLongitude());
		Map<RecommendationCriterion, BigDecimal> scores = new EnumMap<>(RecommendationCriterion.class);
		scores.put(RecommendationCriterion.WORK_TIME,
				workTimeScoreCalculator.calculate(preference.availableWorkTimes(), candidate.requiredWorkTimes()));
		scores.put(RecommendationCriterion.DISTANCE, distanceScore.score());
		scores.put(RecommendationCriterion.WAGE, wageScoreCalculator.calculate(candidate.wageType(), candidate.wageAmount(),
				preference.minimumHourlyWage(), candidate.requiredWorkTimes()));
		scores.put(RecommendationCriterion.BUSINESS_TYPE,
				businessTypeScoreCalculator.calculate(preference.businessTypeFlexible(), preference.preferredBusinessTypes(),
						candidate.businessTypeName(), candidate.otherJobName()));
		BigDecimal matchScore = scoreCalculator.calculate(scores, preference.priorities());
		List<String> reasons = reasonGenerator.generate(preference, scores, distanceScore.distanceKm());
		return new RecommendationResult(candidate.jobPostingId(), matchScore, scores, distanceScore.distanceKm(), reasons);
	}
}
