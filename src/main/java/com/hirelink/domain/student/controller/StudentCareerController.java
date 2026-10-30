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

import com.hirelink.domain.student.dto.StudentCareerCreateRequest;
import com.hirelink.domain.student.dto.StudentCareerResponse;
import com.hirelink.domain.student.service.StudentCareerService;

@RestController
@RequestMapping("/api/students/me/careers")
public class StudentCareerController {

	private final StudentCareerService studentCareerService;

	public StudentCareerController(StudentCareerService studentCareerService) {
		this.studentCareerService = studentCareerService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<StudentCareerResponse> createCareer(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody StudentCareerCreateRequest request) {
		StudentCareerResponse response = studentCareerService.createCareer(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
