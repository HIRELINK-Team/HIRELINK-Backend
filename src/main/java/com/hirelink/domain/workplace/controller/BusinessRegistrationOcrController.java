package com.hirelink.domain.workplace.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.hirelink.domain.workplace.dto.BusinessRegistrationOcrResponse;
import com.hirelink.domain.workplace.service.BusinessRegistrationOcrService;
import com.hirelink.global.exception.InvalidWorkplaceException;

@RestController
public class BusinessRegistrationOcrController {

	private final BusinessRegistrationOcrService ocrService;

	public BusinessRegistrationOcrController(BusinessRegistrationOcrService ocrService) {
		this.ocrService = ocrService;
	}

	@PostMapping(value = "/api/workplaces/business-registration/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<BusinessRegistrationOcrResponse> recognize(@AuthenticationPrincipal Jwt jwt,
			@RequestPart("businessRegistrationFile") MultipartFile file, MultipartHttpServletRequest request) {
		if (request.getMultiFileMap().values().stream().mapToInt(files -> files.size()).sum() != 1
				|| !request.getParameterMap().isEmpty()) {
			throw new InvalidWorkplaceException("사업자등록증 파일 하나만 첨부해주세요.");
		}
		BusinessRegistrationOcrResponse response = ocrService.recognize(Long.valueOf(jwt.getSubject()), file);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
