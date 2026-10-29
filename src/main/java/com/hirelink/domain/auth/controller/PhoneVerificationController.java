package com.hirelink.domain.auth.controller;

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

import com.hirelink.domain.auth.dto.PhoneVerificationRequest;
import com.hirelink.domain.auth.dto.PhoneVerificationRequestResponse;
import com.hirelink.domain.auth.dto.PhoneVerificationConfirmRequest;
import com.hirelink.domain.auth.dto.PhoneVerificationConfirmResponse;
import com.hirelink.domain.auth.service.PhoneVerificationService;

@RestController
@RequestMapping("/api/auth/phone-verifications")
public class PhoneVerificationController {

	private final PhoneVerificationService phoneVerificationService;

	public PhoneVerificationController(PhoneVerificationService phoneVerificationService) {
		this.phoneVerificationService = phoneVerificationService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PhoneVerificationRequestResponse> requestCode(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PhoneVerificationRequest request) {
		phoneVerificationService.requestCode(Long.valueOf(jwt.getSubject()), request.normalizedPhoneNumber());
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new PhoneVerificationRequestResponse(PhoneVerificationService.CODE_TTL.toSeconds(),
						"인증번호가 전송되었습니다."));
	}

	@PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PhoneVerificationConfirmResponse> confirmCode(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PhoneVerificationConfirmRequest request) {
		PhoneVerificationConfirmResponse response = phoneVerificationService.confirmCode(
				Long.valueOf(jwt.getSubject()), request.normalizedPhoneNumber(), request.code());
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
