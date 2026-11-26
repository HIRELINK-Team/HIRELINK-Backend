package com.hirelink.domain.employmentmatch.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.employmentmatch.dto.EmploymentMatchDeleteRequest;
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
@Tag(name = "Employment Matches", description = "채용 완료 조회 및 삭제 API")
public class EmploymentMatchController {

	private final EmploymentMatchService employmentMatchService;

	public EmploymentMatchController(EmploymentMatchService employmentMatchService) {
		this.employmentMatchService = employmentMatchService;
	}

	@GetMapping("/api/matches/{id}")
	@Operation(summary = "채용 완료 상세 조회", description = "채용 완료 당사자인 유학생 또는 사업주가 근무 정보와 상대방 연락처를 조회합니다. 사업주 조회에서 탈퇴 신청 30일 경계부터 studentDataMasked=true이고 유학생 연락처·프로필·희망 시작일은 null입니다.")
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
	@Operation(summary = "내 채용 완료 목록 조회", description = "유학생은 본인의 전체 채용 완료 목록을, 사업주는 본인 공고의 목록을 조회합니다. 채용공고 필터는 사업주만 사용할 수 있습니다. 사업주 조회에서 탈퇴 신청 30일 경계부터 해당 항목의 studentDataMasked=true이고 유학생 정보·희망 시작일은 null입니다.")
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

	@DeleteMapping("/api/job-postings/{jobPostingId}/matches")
	@Operation(summary = "채용 완료 선택 삭제", description = "사업주가 본인 소유 공고에서 선택한 채용 완료를 양쪽 목록·상세 및 자격 만료 문자 대상에서 제외합니다. 이미 삭제된 항목의 재요청은 성공 처리합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "선택 삭제 성공"),
		@ApiResponse(responseCode = "400", description = "공고 ID 또는 요청 본문이 유효하지 않음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "사업주가 아니거나 본인 소유 공고·해당 공고의 채용 건이 아님",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "공고 또는 채용 완료 정보를 찾을 수 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<Void> deleteSelected(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "현재 선택한 채용공고 ID", required = true, example = "301")
			@PathVariable @Positive Long jobPostingId,
			@Valid @RequestBody EmploymentMatchDeleteRequest request) {
		employmentMatchService.deleteSelected(Long.valueOf(jwt.getSubject()), jobPostingId, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/job-postings/{jobPostingId}/matches/all")
	@Operation(summary = "공고별 채용 완료 전체 삭제", description = "사업주가 본인 소유의 현재 선택한 공고에 남아 있는 채용 완료를 하나의 트랜잭션으로 전체 삭제합니다. 삭제할 항목이 없어도 성공 처리합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "전체 삭제 성공"),
		@ApiResponse(responseCode = "400", description = "공고 ID가 유효하지 않음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "사업주가 아니거나 본인 소유 공고가 아님",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "공고를 찾을 수 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<Void> deleteAll(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "현재 선택한 채용공고 ID", required = true, example = "301")
			@PathVariable @Positive Long jobPostingId) {
		employmentMatchService.deleteAll(Long.valueOf(jwt.getSubject()), jobPostingId);
		return ResponseEntity.noContent().build();
	}
}
