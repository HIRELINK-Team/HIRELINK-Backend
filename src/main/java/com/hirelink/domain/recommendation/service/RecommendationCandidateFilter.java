package com.hirelink.domain.recommendation.service;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

@Component
public class RecommendationCandidateFilter {

	public boolean isEligible(RecommendationCandidate candidate, LocalDate evaluationDate) {
		if (candidate == null || evaluationDate == null || candidate.jobPostingId() == null) {
			return false;
		}
		if (candidate.status() != JobPostingStatus.RECRUITING
				|| candidate.applicationDeadlineDate() == null
				|| candidate.applicationDeadlineDate().isBefore(evaluationDate)) {
			return false;
		}
		if (candidate.activeReviewCount() < 0
				|| candidate.activeReviewCount() >= RecommendationPolicyV1.MAX_ACTIVE_REVIEWS
				|| candidate.previouslyApplied()) {
			return false;
		}
		return DistanceScoreCalculator.hasValidCoordinates(candidate.workplaceLatitude(), candidate.workplaceLongitude())
				&& WorkTimeNormalizer.allValid(candidate.requiredWorkTimes())
				&& WageScoreCalculator.isValidWage(candidate.wageType(), candidate.wageAmount(), candidate.requiredWorkTimes());
	}
}
