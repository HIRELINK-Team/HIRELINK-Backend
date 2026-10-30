package com.hirelink.domain.workplace.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyRequest;
import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyResponse;
import com.hirelink.domain.workplace.service.BusinessRegistrationService;

@RestController
@RequestMapping("/api/workplaces/business-registration")
public class BusinessRegistrationController {

	private final BusinessRegistrationService businessRegistrationService;

	public BusinessRegistrationController(BusinessRegistrationService businessRegistrationService) {
		this.businessRegistrationService = businessRegistrationService;
	}

	@PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<BusinessRegistrationVerifyResponse> verify(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody BusinessRegistrationVerifyRequest request) {
		BusinessRegistrationVerifyResponse response = businessRegistrationService.verify(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
