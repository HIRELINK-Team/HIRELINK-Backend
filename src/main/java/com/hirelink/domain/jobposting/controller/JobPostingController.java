package com.hirelink.domain.jobposting.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.jobposting.dto.JobPostingListResponse;
import com.hirelink.domain.jobposting.service.JobPostingService;

@RestController
public class JobPostingController {

	private final JobPostingService jobPostingService;

	public JobPostingController(JobPostingService jobPostingService) {
		this.jobPostingService = jobPostingService;
	}

	@GetMapping("/api/employers/me/job-postings")
	public ResponseEntity<JobPostingListResponse> getMyJobPostings(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String status, @RequestParam(required = false) String includeDraft) {
		JobPostingListResponse response = jobPostingService.getMyJobPostings(Long.valueOf(jwt.getSubject()), status, includeDraft);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
