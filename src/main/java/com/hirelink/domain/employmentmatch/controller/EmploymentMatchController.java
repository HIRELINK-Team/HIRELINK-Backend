package com.hirelink.domain.employmentmatch.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.hirelink.domain.employmentmatch.dto.EmploymentMatchDetailResponse;
import com.hirelink.domain.employmentmatch.dto.EmploymentMatchListResponse;
import com.hirelink.domain.employmentmatch.service.EmploymentMatchService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Employment Matches", description = "채용 완료 상세 및 목록 조회 API")
public class EmploymentMatchController {

	private final EmploymentMatchService employmentMatchService;

	public EmploymentMatchController(EmploymentMatchService employmentMatchService) {
		this.employmentMatchService = employmentMatchService;
	}

	@GetMapping("/api/matches/{id}")
	@Operation(summary = "채용 완료 상세 조회", description = "채용 완료 당사자인 유학생 또는 사업주가 근무 정보와 상대방 연락처를 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "채용 완료 상세 조회 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmploymentMatchDetailResponse.class))),
			@ApiResponse(responseCode = "400", description = "채용 완료 ID가 양수가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "채용 완료 당사자가 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "채용 완료 정보를 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EmploymentMatchDetailResponse> getMatch(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "채용 완료 ID", required = true, example = "501")
			@PathVariable @Positive Long id) {
		EmploymentMatchDetailResponse response = employmentMatchService.getMatch(
				Long.valueOf(jwt.getSubject()), id);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/matches/me")
	@Operation(summary = "내 채용 완료 목록 조회", description = "유학생은 본인의 전체 채용 완료 목록을, 사업주는 본인 공고의 목록을 조회합니다. 채용공고 필터는 사업주만 사용할 수 있습니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 채용 완료 목록 조회 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmploymentMatchListResponse.class))),
			@ApiResponse(responseCode = "400", description = "유학생이 공고 필터를 사용했거나 공고 ID가 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "지원되지 않는 역할이거나 본인 소유 공고가 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "필터로 지정한 채용공고를 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EmploymentMatchListResponse> getMyMatches(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "사업주용 채용공고 ID 필터. 유학생은 사용할 수 없습니다.", example = "301")
			@RequestParam(required = false) Long jobPostingId) {
		EmploymentMatchListResponse response = employmentMatchService.getMyMatches(
				Long.valueOf(jwt.getSubject()), jobPostingId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
