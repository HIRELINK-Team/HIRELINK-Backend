package com.hirelink.domain.application.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.application.dto.ApplicationRejectResponse;
import com.hirelink.domain.application.dto.ApplicationWithdrawResponse;
import com.hirelink.domain.application.service.ApplicationService;

@RestController
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
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
