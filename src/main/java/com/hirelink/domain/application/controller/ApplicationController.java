package com.hirelink.domain.application.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.application.dto.ApplicationCreateRequest;
import com.hirelink.domain.application.dto.ApplicationCreateResponse;
import com.hirelink.domain.application.dto.ApplicationRejectResponse;
import com.hirelink.domain.application.dto.ApplicationWithdrawResponse;
import com.hirelink.domain.application.dto.EmployerApplicationDetailResponse;
import com.hirelink.domain.application.dto.JobPostingApplicationListResponse;
import com.hirelink.domain.application.dto.StudentApplicationDetailResponse;
import com.hirelink.domain.application.dto.StudentApplicationListResponse;
import com.hirelink.domain.application.service.ApplicationService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Applications", description = "유학생 지원 및 사업주 지원자 관리 API")
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping(value = "/api/applications", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "지원 생성", description = "유학생이 본인의 유효한 추천을 선택해 채용공고에 지원합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "지원 생성 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApplicationCreateResponse.class))),
			@ApiResponse(responseCode = "400", description = "요청 필드가 누락되었거나 희망 근무 시작일이 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 본인의 추천이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "추천 또는 채용공고를 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "추천·공고 상태가 유효하지 않거나 중복 지원 또는 진행 가능 인원 초과",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<ApplicationCreateResponse> createApplication(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ApplicationCreateRequest request) {
		ApplicationCreateResponse response = applicationService.createApplication(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/api/applications/{applicationId}/withdraw")
	@Operation(summary = "지원 취소", description = "유학생이 본인의 사업주 응답 대기 중인 지원을 취소합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "지원 취소 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApplicationWithdrawResponse.class))),
			@ApiResponse(responseCode = "400", description = "지원 ID 형식이 올바르지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 본인의 지원이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "지원을 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "사업주 응답 대기 상태가 아니어서 취소할 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<ApplicationWithdrawResponse> withdrawApplication(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "취소할 지원 ID", required = true, example = "201")
			@PathVariable Long applicationId) {
		ApplicationWithdrawResponse response = applicationService.withdrawApplication(
				Long.valueOf(jwt.getSubject()), applicationId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/api/applications/{applicationId}/reject")
	@Operation(summary = "지원 거절", description = "사업주가 본인 채용공고에 접수된 응답 기한 내 지원을 거절합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "지원 거절 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApplicationRejectResponse.class))),
			@ApiResponse(responseCode = "400", description = "지원 ID 형식이 올바르지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "사업주 계정이 아니거나 본인 공고의 지원이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "지원을 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "응답 기한이 지났거나 거절 가능한 지원 상태가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<ApplicationRejectResponse> rejectApplication(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "거절할 지원 ID", required = true, example = "201")
			@PathVariable Long applicationId) {
		ApplicationRejectResponse response = applicationService.rejectApplication(
				Long.valueOf(jwt.getSubject()), applicationId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/employer/applications/{id}")
	@Operation(summary = "사업주용 지원 상세 조회", description = "사업주가 본인 채용공고에 접수된 지원자의 지원 당시 정보와 매칭 결과를 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "지원 상세 조회 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmployerApplicationDetailResponse.class))),
			@ApiResponse(responseCode = "400", description = "지원 ID가 양수가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "사업주 계정이 아니거나 본인 공고의 지원이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "지원을 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EmployerApplicationDetailResponse> getEmployerApplication(
			@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "조회할 지원 ID", required = true, example = "201")
			@PathVariable @Positive Long id) {
		EmployerApplicationDetailResponse response = applicationService.getEmployerApplication(
				Long.valueOf(jwt.getSubject()), id);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/job-postings/{jobId}/applications")
	@Operation(summary = "공고별 지원 목록 조회", description = "사업주가 본인 채용공고의 지원 목록과 상태별 집계를 조회합니다. 상태를 생략하면 종료된 지원은 제외됩니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "공고별 지원 목록 조회 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobPostingApplicationListResponse.class))),
			@ApiResponse(responseCode = "400", description = "공고 ID 또는 지원 상태 필터가 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "사업주 계정이 아니거나 본인의 채용공고가 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "채용공고를 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<JobPostingApplicationListResponse> getJobPostingApplications(
			@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "조회할 채용공고 ID", required = true, example = "301")
			@PathVariable @Positive Long jobId,
			@Parameter(description = "지원 상태 필터: WAITING_RESPONSE(응답 대기), OFFERED(제안 발송), COMPLETED(채용 완료), ENDED(종료)",
					example = "WAITING_RESPONSE", schema = @Schema(allowableValues = {"WAITING_RESPONSE", "OFFERED", "COMPLETED", "ENDED"}))
			@RequestParam(required = false) String status) {
		JobPostingApplicationListResponse response = applicationService.getJobPostingApplications(
				Long.valueOf(jwt.getSubject()), jobId, status);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/applications/{id}")
	@Operation(summary = "유학생용 지원 상세 조회", description = "유학생이 본인의 지원 진행 상태와 채용공고·채용 제안·채용 완료 연결 정보를 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "지원 상세 조회 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentApplicationDetailResponse.class))),
			@ApiResponse(responseCode = "400", description = "지원 ID가 양수가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 본인의 지원이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "지원을 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<StudentApplicationDetailResponse> getStudentApplication(
			@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "조회할 지원 ID", required = true, example = "201")
			@PathVariable @Positive Long id) {
		StudentApplicationDetailResponse response = applicationService.getStudentApplication(
				Long.valueOf(jwt.getSubject()), id);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/applications/me")
	@Operation(summary = "내 지원 목록 조회", description = "유학생이 본인의 지원 목록을 조회합니다. 상태를 생략하면 종료된 지원은 제외됩니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 지원 목록 조회 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentApplicationListResponse.class))),
			@ApiResponse(responseCode = "400", description = "지원 상태 필터가 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<StudentApplicationListResponse> getStudentApplications(
			@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "지원 상태 필터: WAITING_RESPONSE(응답 대기), OFFERED(제안 발송), COMPLETED(채용 완료), ENDED(종료)",
					example = "OFFERED", schema = @Schema(allowableValues = {"WAITING_RESPONSE", "OFFERED", "COMPLETED", "ENDED"}))
			@RequestParam(required = false) String status) {
		StudentApplicationListResponse response = applicationService.getStudentApplications(
				Long.valueOf(jwt.getSubject()), status);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
