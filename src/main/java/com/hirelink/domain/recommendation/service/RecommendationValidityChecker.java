package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.hirelink.domain.recommendation.entity.Recommendation;
import com.hirelink.domain.recommendation.entity.RecommendationBatch;

@Component
public class RecommendationValidityChecker {

	private final RecommendationCalculationService calculationService;

	public RecommendationValidityChecker(RecommendationCalculationService calculationService) {
		this.calculationService = calculationService;
	}

	public boolean isRefreshRequired(Long studentProfileId, RecommendationBatch batch,
			List<Recommendation> storedRecommendations) {
		if (batch.getInvalidatedAt() != null) {
			return true;
		}
		RecommendationCalculationService.Calculation current = calculationService.calculate(studentProfileId);
		if (!current.inputSnapshot().equals(batch.getInputSnapshot())) {
			return true;
		}
		List<RecommendationResult> currentResults = current.results();
		if (currentResults.size() != storedRecommendations.size()) {
			return true;
		}
		for (int index = 0; index < currentResults.size(); index++) {
			RecommendationResult result = currentResults.get(index);
			Recommendation stored = storedRecommendations.get(index);
			if (stored.getRank().intValue() != index + 1
					|| !stored.getJobPosting().getId().equals(result.jobPostingId())
					|| stored.getScore().compareTo(result.matchScore()) != 0
					|| !recommendationReasons(stored.getReasonJson()).equals(result.reasons())) {
				return true;
			}
		}
		return false;
	}

	public List<String> recommendationReasons(Map<String, Object> reasonJson) {
		if (reasonJson == null) {
			return List.of();
		}
		Object reasons = reasonJson.get("recommendationReasons");
		if (!(reasons instanceof List<?> values)) {
			return List.of();
		}
		return values.stream().map(Object::toString).toList();
	}

	public Map<String, Object> reasonSnapshot(RecommendationResult result) {
		Map<String, Object> criterionScores = result.criterionScores().entrySet().stream()
				.collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue,
						(first, second) -> first, java.util.LinkedHashMap::new));
		Map<String, Object> reason = new java.util.LinkedHashMap<>();
		reason.put("recommendationReasons", result.reasons());
		reason.put("criterionScores", criterionScores);
		reason.put("distanceKm", BigDecimal.valueOf(result.distanceKm()));
		return reason;
	}
}
