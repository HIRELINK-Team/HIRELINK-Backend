package com.hirelink.domain.jobposting.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.jobposting.dto.JobPostingCreateRequest;
import com.hirelink.domain.jobposting.dto.JobPostingCreateResponse;
import com.hirelink.domain.jobposting.dto.JobPostingDetailResponse;
import com.hirelink.domain.jobposting.dto.JobPostingListResponse;
import com.hirelink.domain.jobposting.dto.JobPostingUpdateRequest;
import com.hirelink.domain.jobposting.dto.JobPostingUpdateResponse;
import com.hirelink.domain.jobposting.service.JobPostingService;

@RestController
public class JobPostingController {

	private final JobPostingService jobPostingService;

	public JobPostingController(JobPostingService jobPostingService) {
		this.jobPostingService = jobPostingService;
	}

	@PostMapping(value = "/api/job-postings", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JobPostingCreateResponse> createJobPosting(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody JobPostingCreateRequest request) {
		JobPostingCreateResponse response = jobPostingService.createJobPosting(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/employers/me/job-postings")
	public ResponseEntity<JobPostingListResponse> getMyJobPostings(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status, @RequestParam(required = false) String includeDraft) {
		JobPostingListResponse response = jobPostingService.getMyJobPostings(Long.valueOf(jwt.getSubject()), status, includeDraft);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping(value = "/api/job-postings/{jobPostingId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JobPostingUpdateResponse> updateJobPosting(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long jobPostingId, @Valid @RequestBody JobPostingUpdateRequest request) {
		JobPostingUpdateResponse response = jobPostingService.updateJobPosting(Long.valueOf(jwt.getSubject()), jobPostingId, request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/job-postings/{jobPostingId}")
	public ResponseEntity<JobPostingDetailResponse> getJobPosting(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long jobPostingId) {
		JobPostingDetailResponse response = jobPostingService.getJobPosting(Long.valueOf(jwt.getSubject()), jobPostingId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
