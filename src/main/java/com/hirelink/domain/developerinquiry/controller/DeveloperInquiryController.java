package com.hirelink.domain.developerinquiry.controller;

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

import com.hirelink.domain.developerinquiry.dto.DeveloperInquiryCreateRequest;
import com.hirelink.domain.developerinquiry.dto.DeveloperInquiryCreateResponse;
import com.hirelink.domain.developerinquiry.service.DeveloperInquiryService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/developer-inquiries")
@Tag(name = "Developer Inquiries", description = "유학생·사업주 공통 개발자 문의 API")
public class DeveloperInquiryController {

	private final DeveloperInquiryService inquiryService;

	public DeveloperInquiryController(DeveloperInquiryService inquiryService) {
		this.inquiryService = inquiryService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "개발자 문의 접수",
			description = "로그인한 유학생 또는 사업주가 문의를 접수합니다. 작성자·회원 유형·답변 이메일은 현재 인증된 계정에서 가져옵니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "문의 접수 성공",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = DeveloperInquiryCreateResponse.class),
						examples = @ExampleObject(value = "{\"inquiryId\":101,\"submittedAt\":\"2026-11-24T03:00:00Z\"}"))),
		@ApiResponse(responseCode = "400", description = "문의 유형·제목·내용이 누락되었거나 길이 제한을 초과함",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "탈퇴한 계정",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "409", description = "계정에 답변을 받을 이메일이 등록되어 있지 않음",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<DeveloperInquiryCreateResponse> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DeveloperInquiryCreateRequest request) {
		DeveloperInquiryCreateResponse response = inquiryService.create(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
