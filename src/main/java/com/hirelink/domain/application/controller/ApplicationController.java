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

@RestController
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping(value = "/api/applications", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApplicationCreateResponse> createApplication(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ApplicationCreateRequest request) {
		ApplicationCreateResponse response = applicationService.createApplication(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/api/applications/{applicationId}/withdraw")
	public ResponseEntity<ApplicationWithdrawResponse> withdrawApplication(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long applicationId) {
		ApplicationWithdrawResponse response = applicationService.withdrawApplication(
				Long.valueOf(jwt.getSubject()), applicationId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/api/applications/{applicationId}/reject")
	public ResponseEntity<ApplicationRejectResponse> rejectApplication(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long applicationId) {
		ApplicationRejectResponse response = applicationService.rejectApplication(
				Long.valueOf(jwt.getSubject()), applicationId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/employer/applications/{id}")
	public ResponseEntity<EmployerApplicationDetailResponse> getEmployerApplication(
			@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
		EmployerApplicationDetailResponse response = applicationService.getEmployerApplication(
				Long.valueOf(jwt.getSubject()), id);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/job-postings/{jobId}/applications")
	public ResponseEntity<JobPostingApplicationListResponse> getJobPostingApplications(
			@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long jobId,
			@RequestParam(required = false) String status) {
		JobPostingApplicationListResponse response = applicationService.getJobPostingApplications(
				Long.valueOf(jwt.getSubject()), jobId, status);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/applications/{id}")
	public ResponseEntity<StudentApplicationDetailResponse> getStudentApplication(
			@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
		StudentApplicationDetailResponse response = applicationService.getStudentApplication(
				Long.valueOf(jwt.getSubject()), id);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/applications/me")
	public ResponseEntity<StudentApplicationListResponse> getStudentApplications(
			@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String status) {
		StudentApplicationListResponse response = applicationService.getStudentApplications(
				Long.valueOf(jwt.getSubject()), status);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
