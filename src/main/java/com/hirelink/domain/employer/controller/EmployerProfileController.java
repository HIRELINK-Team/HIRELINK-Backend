package com.hirelink.domain.employer.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.employer.dto.EmployerProfileCreateRequest;
import com.hirelink.domain.employer.dto.EmployerProfileCreateResponse;
import com.hirelink.domain.employer.dto.EmployerProfileResponse;
import com.hirelink.domain.employer.service.EmployerProfileService;

@RestController
@RequestMapping("/api/employers/me/profile")
public class EmployerProfileController {

	private final EmployerProfileService employerProfileService;

	public EmployerProfileController(EmployerProfileService employerProfileService) {
		this.employerProfileService = employerProfileService;
	}

	@GetMapping
	public ResponseEntity<EmployerProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
		EmployerProfileResponse response = employerProfileService.getProfile(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<EmployerProfileCreateResponse> createProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody EmployerProfileCreateRequest request) {
		EmployerProfileCreateResponse response = employerProfileService.createProfile(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
