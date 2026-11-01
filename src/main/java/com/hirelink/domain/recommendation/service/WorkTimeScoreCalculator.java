package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.hirelink.domain.recommendation.service.WorkTimeNormalizer.MinuteRange;

@Component
public class WorkTimeScoreCalculator {

	public BigDecimal calculate(List<RecommendationTimeRange> availableWorkTimes,
			List<RecommendationTimeRange> requiredWorkTimes) {
		Map<String, List<MinuteRange>> available = WorkTimeNormalizer.normalize(availableWorkTimes);
		Map<String, List<MinuteRange>> required = WorkTimeNormalizer.normalize(requiredWorkTimes);
		long totalRequiredMinutes = WorkTimeNormalizer.totalMinutes(required);
		if (totalRequiredMinutes == 0) {
			throw new IllegalArgumentException("공고 요구 근무시간은 한 구간 이상이어야 합니다.");
		}
		long overlapMinutes = required.entrySet().stream()
				.mapToLong(entry -> overlap(entry.getValue(), available.getOrDefault(entry.getKey(), List.of())))
				.sum();
		BigDecimal score = BigDecimal.valueOf(overlapMinutes)
				.multiply(RecommendationScoreSupport.HUNDRED)
				.divide(BigDecimal.valueOf(totalRequiredMinutes), 10, RoundingMode.HALF_UP);
		return RecommendationScoreSupport.clamp(score);
	}

	private long overlap(List<MinuteRange> required, List<MinuteRange> available) {
		long total = 0;
		int requiredIndex = 0;
		int availableIndex = 0;
		while (requiredIndex < required.size() && availableIndex < available.size()) {
			MinuteRange job = required.get(requiredIndex);
			MinuteRange student = available.get(availableIndex);
			total += Math.max(0, Math.min(job.end(), student.end()) - Math.max(job.start(), student.start()));
			if (job.end() <= student.end()) {
				requiredIndex++;
			} else {
				availableIndex++;
			}
		}
		return total;
	}
}
