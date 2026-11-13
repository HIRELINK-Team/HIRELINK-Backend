package com.hirelink.domain.recommendation.service;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {

	@Test
	void autoRefreshExpiresAtExactlyTwentyFourHours() {
		Instant generatedAt = Instant.parse("2026-11-13T00:00:00Z");

		assertThat(RecommendationService.isAutoRefreshExpired(
				generatedAt, generatedAt.plus(Duration.ofHours(24)).minusNanos(1))).isFalse();
		assertThat(RecommendationService.isAutoRefreshExpired(
				generatedAt, generatedAt.plus(Duration.ofHours(24)))).isTrue();
		assertThat(RecommendationService.isAutoRefreshExpired(
				generatedAt, generatedAt.plus(Duration.ofHours(24)).plusNanos(1))).isTrue();
	}
}
