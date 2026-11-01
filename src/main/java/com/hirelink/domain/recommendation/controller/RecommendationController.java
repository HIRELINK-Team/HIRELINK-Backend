package com.hirelink.domain.recommendation.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.recommendation.dto.RecommendationCreateResponse;
import com.hirelink.domain.recommendation.dto.RecommendationDetailResponse;
import com.hirelink.domain.recommendation.dto.RecommendationListResponse;
import com.hirelink.domain.recommendation.dto.RecommendationRefreshResponse;
import com.hirelink.domain.recommendation.service.RecommendationService;

@RestController
public class RecommendationController {

	private final RecommendationService recommendationService;

	public RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@PostMapping("/api/recommendations")
	public ResponseEntity<RecommendationCreateResponse> createRecommendations(@AuthenticationPrincipal Jwt jwt) {
		RecommendationCreateResponse response = recommendationService.createInitialRecommendations(
				Long.valueOf(jwt.getSubject()));
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PostMapping("/api/recommendations/refresh")
	public ResponseEntity<RecommendationRefreshResponse> refreshRecommendations(@AuthenticationPrincipal Jwt jwt) {
		RecommendationRefreshResponse response = recommendationService.refreshRecommendations(
				Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/recommendations")
	public ResponseEntity<RecommendationListResponse> getRecommendations(@AuthenticationPrincipal Jwt jwt) {
		RecommendationListResponse response = recommendationService.getRecommendations(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/recommendations/{id}")
	public ResponseEntity<RecommendationDetailResponse> getRecommendation(@AuthenticationPrincipal Jwt jwt,
			@PathVariable @Positive Long id) {
		RecommendationDetailResponse response = recommendationService.getRecommendation(
				Long.valueOf(jwt.getSubject()), id);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
