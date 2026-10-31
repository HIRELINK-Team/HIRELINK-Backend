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

@RestController
public class EmploymentMatchController {

	private final EmploymentMatchService employmentMatchService;

	public EmploymentMatchController(EmploymentMatchService employmentMatchService) {
		this.employmentMatchService = employmentMatchService;
	}

	@GetMapping("/api/matches/{id}")
	public ResponseEntity<EmploymentMatchDetailResponse> getMatch(@AuthenticationPrincipal Jwt jwt,
			@PathVariable @Positive Long id) {
		EmploymentMatchDetailResponse response = employmentMatchService.getMatch(
				Long.valueOf(jwt.getSubject()), id);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/matches/me")
	public ResponseEntity<EmploymentMatchListResponse> getMyMatches(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Long jobPostingId) {
		EmploymentMatchListResponse response = employmentMatchService.getMyMatches(
				Long.valueOf(jwt.getSubject()), jobPostingId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
