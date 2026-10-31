package com.hirelink.domain.employmentoffer.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.employmentoffer.dto.EmploymentOfferAcceptResponse;
import com.hirelink.domain.employmentoffer.dto.EmploymentOfferCreateResponse;
import com.hirelink.domain.employmentoffer.dto.EmploymentOfferRejectResponse;
import com.hirelink.domain.employmentoffer.service.EmploymentOfferService;

@RestController
public class EmploymentOfferController {

	private final EmploymentOfferService employmentOfferService;

	public EmploymentOfferController(EmploymentOfferService employmentOfferService) {
		this.employmentOfferService = employmentOfferService;
	}

	@PostMapping("/api/applications/{applicationId}/offers")
	public ResponseEntity<EmploymentOfferCreateResponse> createOffer(@AuthenticationPrincipal Jwt jwt,
			@PathVariable @Positive Long applicationId) {
		EmploymentOfferCreateResponse response = employmentOfferService.createOffer(
				Long.valueOf(jwt.getSubject()), applicationId);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/api/offers/{offerId}/accept")
	public ResponseEntity<EmploymentOfferAcceptResponse> acceptOffer(@AuthenticationPrincipal Jwt jwt,
			@PathVariable @Positive Long offerId) {
		EmploymentOfferAcceptResponse response = employmentOfferService.acceptOffer(
				Long.valueOf(jwt.getSubject()), offerId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/api/offers/{offerId}/reject")
	public ResponseEntity<EmploymentOfferRejectResponse> rejectOffer(@AuthenticationPrincipal Jwt jwt,
			@PathVariable @Positive Long offerId) {
		EmploymentOfferRejectResponse response = employmentOfferService.rejectOffer(
				Long.valueOf(jwt.getSubject()), offerId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
