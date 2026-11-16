package com.hirelink.domain.workplace.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyRequest;
import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyResponse;
import com.hirelink.domain.workplace.service.BusinessRegistrationService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Business Registration", description = "사업자등록번호 확인 및 등록증 OCR")
@RestController
@RequestMapping("/api/workplaces/business-registration")
public class BusinessRegistrationController {

	private final BusinessRegistrationService businessRegistrationService;

	public BusinessRegistrationController(BusinessRegistrationService businessRegistrationService) {
		this.businessRegistrationService = businessRegistrationService;
	}

	@Operation(summary = "사업자등록번호 확인",
			description = "로그인한 사업주가 번호의 현재 영업 상태를 조회한다. 숫자 10자리 또는 XXX-XX-XXXXX 형식, 앞뒤 공백 제거 후 조회한다. 정상 조회 시 영업 중이면 valid=true, 휴업·폐업·미등록이면 valid=false인 200 응답이다. 국세청 조회 실패·설정 누락·응답 오류는 502다. 결과를 저장하거나 사업장을 만들지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = BusinessRegistrationVerifyResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"businessRegistrationNumber\":\"123-45-67890\",\"valid\":true,\"message\":\"확인 가능한 사업자등록번호입니다.\"}"))),
		@ApiResponse(responseCode = "400", description = "입력 형식 또는 값 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"요청 형식이 올바르지 않습니다.\"}")
						})),
		@ApiResponse(responseCode = "401", description = "유효한 인증 정보 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "UNAUTHORIZED", value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}")
						})),
		@ApiResponse(responseCode = "403", description = "접근 권한 없음 / 탈퇴한 계정",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "FORBIDDEN", value = "{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"),
							@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						})),
		@ApiResponse(responseCode = "502", description = "국세청 조회 실패",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "BUSINESS_REGISTRATION_LOOKUP_FAILED", value = "{\"code\":\"BUSINESS_REGISTRATION_LOOKUP_FAILED\",\"message\":\"사업자등록 상태를 조회하지 못했습니다. 잠시 후 다시 시도해주세요.\"}")
						}))
	})
	@PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<BusinessRegistrationVerifyResponse> verify(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody BusinessRegistrationVerifyRequest request) {
		BusinessRegistrationVerifyResponse response = businessRegistrationService.verify(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
