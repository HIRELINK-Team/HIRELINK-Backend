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
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Recommendations", description = "유학생 맞춤 채용공고 추천 API")
public class RecommendationController {

	private final RecommendationService recommendationService;

	public RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@PostMapping("/api/recommendations")
	@Operation(summary = "최초 추천 생성", description = "유학생이 프로필·매칭 조건·우선순위를 기준으로 최초 추천 배치를 생성합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "최초 추천 생성 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecommendationCreateResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "최초 추천이 이미 존재하거나 추천 필수 정보가 미완성",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<RecommendationCreateResponse> createRecommendations(@AuthenticationPrincipal Jwt jwt) {
		RecommendationCreateResponse response = recommendationService.createInitialRecommendations(
				Long.valueOf(jwt.getSubject()));
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PostMapping("/api/recommendations/refresh")
	@Operation(summary = "추천 갱신", description = "유학생의 최신 매칭 조건으로 추천을 다시 계산합니다. 갱신이 불필요하면 기존 배치를 반환합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "추천 갱신 처리 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecommendationRefreshResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "기존 추천 배치를 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "추천 재계산에 필요한 프로필 또는 매칭 조건이 미완성",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<RecommendationRefreshResponse> refreshRecommendations(@AuthenticationPrincipal Jwt jwt) {
		RecommendationRefreshResponse response = recommendationService.refreshRecommendations(
				Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/recommendations")
	@Operation(summary = "추천 목록 조회", description = "유학생이 자신의 최신 추천 목록을 조회합니다. 생성 후 24시간이 지나면 최신 조건으로 자동 갱신될 수 있습니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "추천 목록 조회 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecommendationListResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "추천 배치를 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "자동 갱신에 필요한 프로필 또는 매칭 조건이 미완성",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<RecommendationListResponse> getRecommendations(@AuthenticationPrincipal Jwt jwt) {
		RecommendationListResponse response = recommendationService.getRecommendations(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/recommendations/{id}")
	@Operation(summary = "추천 상세 조회", description = "유학생이 자신의 최신 추천 배치에 포함된 추천 상세를 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "추천 상세 조회 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecommendationDetailResponse.class))),
			@ApiResponse(responseCode = "400", description = "추천 ID가 양수가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 본인의 추천이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "추천 또는 최신 추천 배치를 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "이전 배치의 추천이거나 현재 조건에 맞게 추천 갱신이 필요함",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<RecommendationDetailResponse> getRecommendation(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "추천 ID", required = true, example = "101")
			@PathVariable @Positive Long id) {
		RecommendationDetailResponse response = recommendationService.getRecommendation(
				Long.valueOf(jwt.getSubject()), id);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
