package com.hirelink.domain.application.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.application.dto.ApplicationCreateRequest;
import com.hirelink.domain.application.dto.ApplicationCreateResponse;
import com.hirelink.domain.application.dto.ApplicationRejectResponse;
import com.hirelink.domain.application.dto.ApplicationWithdrawResponse;
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
}
