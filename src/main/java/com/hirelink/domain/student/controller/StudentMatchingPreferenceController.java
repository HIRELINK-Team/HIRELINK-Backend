package com.hirelink.domain.student.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceUpdateRequest;
import com.hirelink.domain.student.service.StudentMatchingPreferenceService;

@RestController
@RequestMapping("/api/students/me/matching-preferences")
public class StudentMatchingPreferenceController {

	private final StudentMatchingPreferenceService studentMatchingPreferenceService;

	public StudentMatchingPreferenceController(StudentMatchingPreferenceService studentMatchingPreferenceService) {
		this.studentMatchingPreferenceService = studentMatchingPreferenceService;
	}

	@GetMapping
	public ResponseEntity<StudentMatchingPreferenceResponse> getPreferences(@AuthenticationPrincipal Jwt jwt) {
		StudentMatchingPreferenceResponse response = studentMatchingPreferenceService.getPreferences(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<StudentMatchingPreferenceResponse> updatePreferences(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody StudentMatchingPreferenceUpdateRequest request) {
		StudentMatchingPreferenceResponse response = studentMatchingPreferenceService.updatePreferences(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
