package com.hirelink.domain.student.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.dto.StudentProfileCreateRequest;
import com.hirelink.domain.student.dto.StudentProfileCreateResponse;
import com.hirelink.domain.student.service.StudentProfileService;

@RestController
@RequestMapping("/api/students/me/profile")
public class StudentProfileController {

	private final StudentProfileService studentProfileService;

	public StudentProfileController(StudentProfileService studentProfileService) {
		this.studentProfileService = studentProfileService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<StudentProfileCreateResponse> createProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody StudentProfileCreateRequest request) {
		StudentProfileCreateResponse response = studentProfileService.createProfile(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
