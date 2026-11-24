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
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Business Registration", description = "사업자등록번호 확인 및 등록증 OCR")
@RestController
public class BusinessRegistrationOcrController {

	private final BusinessRegistrationOcrService ocrService;

	public BusinessRegistrationOcrController(BusinessRegistrationOcrService ocrService) {
		this.ocrService = ocrService;
	}

	@Operation(summary = "사업자등록증 OCR",
			description = "로그인한 사업주가 businessRegistrationFile 파트로 PDF·JPG·PNG 한 개(최대 5MB)를 전송한다. 추가 파일·폼 필드는 허용하지 않는다. 등록번호만 추출한다. workplaceAddress는 이전 응답 호환 필드로 새 OCR에서는 null이며 주소는 별도로 검색·입력한다. 번호를 읽지 못하면 422, OCR 연결·응답 실패는 502다. OCR 결과는 진위 확인 결과가 아니며 사용자가 확인·수정해야 한다. 원본은 요청 처리 후 보관하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = BusinessRegistrationOcrResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"businessRegistrationNumber\":\"123-45-67890\",\"workplaceAddress\":\"서울특별시 성동구 예시로 10\"}"))),
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
		@ApiResponse(responseCode = "413", description = "파일 크기 제한 초과",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "FILE_TOO_LARGE", value = "{\"code\":\"FILE_TOO_LARGE\",\"message\":\"사업자등록증 파일은 5MB 이하여야 합니다.\"}")
						})),
		@ApiResponse(responseCode = "415", description = "지원하지 않는 파일 형식",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "UNSUPPORTED_FILE_TYPE", value = "{\"code\":\"UNSUPPORTED_FILE_TYPE\",\"message\":\"사업자등록증은 PDF, JPG, PNG 파일만 사용할 수 있습니다.\"}")
						})),
		@ApiResponse(responseCode = "422", description = "등록증에서 사업자번호 인식 실패",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "UNREADABLE_BUSINESS_REGISTRATION", value = "{\"code\":\"UNREADABLE_BUSINESS_REGISTRATION\",\"message\":\"사업자등록증에서 사업자등록번호를 확인하지 못했습니다. 선명한 파일로 다시 시도해주세요.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						})),
		@ApiResponse(responseCode = "502", description = "OCR 서비스 처리 실패",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "BUSINESS_REGISTRATION_OCR_FAILED", value = "{\"code\":\"BUSINESS_REGISTRATION_OCR_FAILED\",\"message\":\"사업자등록증 인식 서비스를 이용할 수 없습니다.\"}")
						}))
	})
	@PostMapping(value = "/api/workplaces/business-registration/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<BusinessRegistrationOcrResponse> recognize(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "사업자등록증 PDF·JPG·PNG 한 개, 최대 5MB", schema = @Schema(type = "string", format = "binary"))
			@RequestPart("businessRegistrationFile") MultipartFile file, @Parameter(hidden = true) MultipartHttpServletRequest request) {
		if (request.getMultiFileMap().values().stream().mapToInt(files -> files.size()).sum() != 1
				|| !request.getParameterMap().isEmpty()) {
			throw new InvalidWorkplaceException("사업자등록증 파일 하나만 첨부해주세요.");
		}
		BusinessRegistrationOcrResponse response = ocrService.recognize(Long.valueOf(jwt.getSubject()), file);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
