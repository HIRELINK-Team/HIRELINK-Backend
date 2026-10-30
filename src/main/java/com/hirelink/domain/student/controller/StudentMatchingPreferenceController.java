package com.hirelink.domain.student.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse;
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
}
