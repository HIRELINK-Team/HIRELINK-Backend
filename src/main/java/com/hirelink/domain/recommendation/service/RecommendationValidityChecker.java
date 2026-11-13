package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
		return isRefreshRequired(batch, storedRecommendations, calculationService.calculate(studentProfileId));
	}

	public boolean isRefreshRequired(RecommendationBatch batch, List<Recommendation> storedRecommendations,
			RecommendationCalculationService.Calculation current) {
		if (batch.getInvalidatedAt() != null) {
			return true;
		}
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

	public boolean isRefreshRequiredForRead(RecommendationBatch batch, List<Recommendation> storedRecommendations,
			RecommendationCalculationService.Calculation current) {
		if (batch.getInvalidatedAt() != null) {
			return true;
		}
		if (hasPriorityChanged(batch, current)
				&& snapshotsWithoutPriorities(batch.getInputSnapshot())
						.equals(snapshotsWithoutPriorities(current.inputSnapshot()))) {
			return false;
		}
		return isRefreshRequired(batch, storedRecommendations, current);
	}

	public boolean isRefreshRequiredForRead(Long studentProfileId, RecommendationBatch batch,
			List<Recommendation> storedRecommendations) {
		return isRefreshRequiredForRead(batch, storedRecommendations,
				calculationService.calculate(studentProfileId));
	}

	public boolean hasPriorityChanged(RecommendationBatch batch,
			RecommendationCalculationService.Calculation current) {
		return !Objects.equals(batch.getInputSnapshot().get("priorities"), current.inputSnapshot().get("priorities"))
				|| !Objects.equals(batch.getInputSnapshot().get("priorityWeights"),
						current.inputSnapshot().get("priorityWeights"));
	}

	private Map<String, Object> snapshotsWithoutPriorities(Map<String, Object> snapshot) {
		Map<String, Object> copy = new LinkedHashMap<>(snapshot);
		copy.remove("priorities");
		copy.remove("priorityWeights");
		return copy;
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
