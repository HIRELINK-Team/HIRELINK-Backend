package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.hirelink.domain.recommendation.service.RecommendationPreference.PreferredBusinessType;

@Component
public class BusinessTypeScoreCalculator {

	public BigDecimal calculate(boolean businessTypeFlexible, List<PreferredBusinessType> preferredBusinessTypes,
			String jobBusinessTypeName, String jobOtherJobName) {
		if (businessTypeFlexible) {
			return RecommendationScoreSupport.HUNDRED;
		}
		boolean matches = preferredBusinessTypes.stream()
				.anyMatch(preferred -> matches(preferred, jobBusinessTypeName, jobOtherJobName));
		return matches ? RecommendationScoreSupport.HUNDRED : RecommendationScoreSupport.ZERO;
	}

	private boolean matches(PreferredBusinessType preferred, String jobBusinessTypeName, String jobOtherJobName) {
		if (preferred == null || !normalize(preferred.businessTypeName()).equals(normalize(jobBusinessTypeName))) {
			return false;
		}
		if (!"other".equals(normalize(jobBusinessTypeName))) {
			return true;
		}
		String preferredOtherName = normalize(preferred.otherJobName());
		return !preferredOtherName.isEmpty() && preferredOtherName.equals(normalize(jobOtherJobName));
	}

	private String normalize(String value) {
		if (value == null) {
			return "";
		}
		return Normalizer.normalize(value, Normalizer.Form.NFKC)
				.replaceAll("\\s+", "")
				.toLowerCase(Locale.ROOT);
	}
}
