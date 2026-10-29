package com.hirelink.domain.member.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.member.dto.TermAgreementRequest;
import com.hirelink.domain.member.dto.TermAgreementResponse;
import com.hirelink.domain.member.service.TermAgreementService;

@RestController
@RequestMapping("/api/users/me/term-agreements")
public class TermAgreementController {

	private final TermAgreementService termAgreementService;

	public TermAgreementController(TermAgreementService termAgreementService) {
		this.termAgreementService = termAgreementService;
	}

	@PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TermAgreementResponse> saveAgreements(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody TermAgreementRequest request) {
		TermAgreementResponse response = termAgreementService.saveAgreements(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
