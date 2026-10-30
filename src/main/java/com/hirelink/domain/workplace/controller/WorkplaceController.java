package com.hirelink.domain.workplace.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.workplace.dto.WorkplaceDetailResponse;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse;
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
}
