package com.hirelink.domain.workplace.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.workplace.dto.WorkplaceDetailResponse;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse;
import com.hirelink.domain.workplace.dto.WorkplaceUpdateRequest;
import com.hirelink.domain.workplace.service.WorkplaceService;

@RestController
public class WorkplaceController {

	private final WorkplaceService workplaceService;

	public WorkplaceController(WorkplaceService workplaceService) {
		this.workplaceService = workplaceService;
	}

	@GetMapping("/api/employers/me/workplaces")
	public ResponseEntity<WorkplaceListResponse> getMyWorkplaces(@AuthenticationPrincipal Jwt jwt) {
		WorkplaceListResponse response = workplaceService.getMyWorkplaces(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@GetMapping("/api/workplaces/{workplaceId}")
	public ResponseEntity<WorkplaceDetailResponse> getWorkplace(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long workplaceId) {
		WorkplaceDetailResponse response = workplaceService.getWorkplace(Long.valueOf(jwt.getSubject()), workplaceId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping(value = "/api/workplaces/{workplaceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<WorkplaceDetailResponse> updateWorkplace(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long workplaceId, @Valid @RequestBody WorkplaceUpdateRequest request) {
		WorkplaceDetailResponse response = workplaceService.updateWorkplace(Long.valueOf(jwt.getSubject()), workplaceId, request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
