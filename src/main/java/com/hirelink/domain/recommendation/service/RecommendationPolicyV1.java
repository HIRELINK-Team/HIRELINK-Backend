package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.List;

public final class RecommendationPolicyV1 {

	public static final int MAX_ACTIVE_REVIEWS = 5;
	public static final double EARTH_RADIUS_KM = 6371.0088;
	public static final double DISTANCE_DECAY_KM = 3.0;
	public static final BigDecimal WEEKS_PER_MONTH = new BigDecimal("4.345");
	public static final int MAX_RECOMMENDATIONS = 3;
	public static final int MATCH_SCORE_SCALE = 1;
	public static final String WORK_TIME_REASON = "요청한 근무시간에 일할 수 있어요";
	public static final String WAGE_REASON = "희망 급여 조건을 충족해요";
	public static final String DISTANCE_REASON = "희망 근무지역에 있어요";
	public static final String BUSINESS_TYPE_REASON = "선호하는 업종이에요";

	private static final List<BigDecimal> PRIORITY_WEIGHTS = List.of(
			new BigDecimal("0.4"),
			new BigDecimal("0.3"),
			new BigDecimal("0.2"),
			new BigDecimal("0.1"));

	private RecommendationPolicyV1() {
	}

	public static BigDecimal priorityWeight(int priorityOrder) {
		if (priorityOrder < 1 || priorityOrder > PRIORITY_WEIGHTS.size()) {
			throw new IllegalArgumentException("우선순위는 1부터 4까지여야 합니다.");
		}
		return PRIORITY_WEIGHTS.get(priorityOrder - 1);
	}
}
