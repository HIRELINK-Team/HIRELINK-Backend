package com.hirelink.domain.workplace.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.workplace.dto.WorkplaceDetailResponse;
import com.hirelink.domain.workplace.dto.WorkplaceCreateRequest;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse;
import com.hirelink.domain.workplace.dto.WorkplaceUpdateRequest;
import com.hirelink.domain.workplace.service.WorkplaceService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Workplaces", description = "사업주 본인 사업장 관리")
@RestController
public class WorkplaceController {

	private final WorkplaceService workplaceService;

	public WorkplaceController(WorkplaceService workplaceService) {
		this.workplaceService = workplaceService;
	}

	@Operation(summary = "사업장 등록",
			description = "로그인한 사업주가 JSON으로 본인 사업장을 등록한다. 사업자번호는 국세청에 다시 조회하며 현재 영업 중일 때만 등록한다. 휴업·폐업·미등록은 400, 이미 등록된 번호는 409다. 원본 파일·대표자명은 받지 않는다. OCR 자동 입력값은 사용자가 확인·수정한 후 전송한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkplaceDetailResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"workplaceId\":1,\"workplaceName\":\"예시카페\",\"businessRegistrationNumber\":\"123-45-67890\",\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"employeeCountRange\":\"UNDER_5\",\"workplaceAddress\":\"서울특별시 성동구 예시로 10\",\"latitude\":37.5665,\"longitude\":126.978,\"workplaceAddressDetail\":null}"))),
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
		@ApiResponse(responseCode = "404", description = "업종 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "BUSINESS_TYPE_NOT_FOUND", value = "{\"code\":\"BUSINESS_TYPE_NOT_FOUND\",\"message\":\"업종 정보를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "409", description = "이미 등록된 사업자번호",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "DUPLICATE_BUSINESS_REGISTRATION_NUMBER", value = "{\"code\":\"DUPLICATE_BUSINESS_REGISTRATION_NUMBER\",\"message\":\"이미 등록된 사업자등록번호입니다.\"}")
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
	@PostMapping(value = "/api/workplaces", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<WorkplaceDetailResponse> createWorkplace(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody WorkplaceCreateRequest request) {
		WorkplaceDetailResponse response = workplaceService.createWorkplace(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "내 사업장 목록 조회",
			description = "로그인한 사업주 본인의 사업장만 ID 오름차순으로 반환한다. 없으면 workplaces=[]이며 페이지네이션은 없다. 국세청·OCR·파일 저장소는 호출하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkplaceListResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"workplaces\":[{\"workplaceId\":1,\"workplaceName\":\"예시카페\",\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"employeeCountRange\":\"UNDER_5\",\"workplaceAddress\":\"서울특별시 성동구 예시로 10\"}]}"))),
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
						}))
	})
	@GetMapping("/api/employers/me/workplaces")
	public ResponseEntity<WorkplaceListResponse> getMyWorkplaces(@AuthenticationPrincipal Jwt jwt) {
		WorkplaceListResponse response = workplaceService.getMyWorkplaces(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "사업장 상세 조회",
			description = "로그인한 사업주가 본인 사업장을 조회한다. 사업자번호는 하이픈 포함으로 반환하며 대표자명·등록증 원본·파일 경로는 반환하지 않는다. 타인 사업장은 403, 없는 사업장은 404다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkplaceDetailResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"workplaceId\":1,\"workplaceName\":\"예시카페\",\"businessRegistrationNumber\":\"123-45-67890\",\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"employeeCountRange\":\"UNDER_5\",\"workplaceAddress\":\"서울특별시 성동구 예시로 10\",\"latitude\":37.5665,\"longitude\":126.978,\"workplaceAddressDetail\":null}"))),
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
		@ApiResponse(responseCode = "404", description = "사업장 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "WORKPLACE_NOT_FOUND", value = "{\"code\":\"WORKPLACE_NOT_FOUND\",\"message\":\"사업장 정보를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@GetMapping("/api/workplaces/{workplaceId}")
	public ResponseEntity<WorkplaceDetailResponse> getWorkplace(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "사업장 ID (Long 정수)", example = "1") @PathVariable Long workplaceId) {
		WorkplaceDetailResponse response = workplaceService.getWorkplace(Long.valueOf(jwt.getSubject()), workplaceId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "사업장 정보 수정",
			description = "보내지 않은 필드는 유지한다. 빈 요청과 수정 대상이 아닌 필드는 400이다. 본인 사업장명·업종·주소·위도·경도만 수정 가능하다. null은 불가하며 주소·위도·경도 중 하나라도 수정하면 세 필드를 함께 보낸다. 사업자번호·직원 수는 유지하고 국세청·OCR은 호출하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkplaceDetailResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"workplaceId\":1,\"workplaceName\":\"예시카페\",\"businessRegistrationNumber\":\"123-45-67890\",\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"employeeCountRange\":\"UNDER_5\",\"workplaceAddress\":\"서울특별시 성동구 예시로 10\",\"latitude\":37.5665,\"longitude\":126.978,\"workplaceAddressDetail\":null}"))),
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
		@ApiResponse(responseCode = "404", description = "사업장 없음 / 업종 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "WORKPLACE_NOT_FOUND", value = "{\"code\":\"WORKPLACE_NOT_FOUND\",\"message\":\"사업장 정보를 찾을 수 없습니다.\"}"),
							@ExampleObject(name = "BUSINESS_TYPE_NOT_FOUND", value = "{\"code\":\"BUSINESS_TYPE_NOT_FOUND\",\"message\":\"업종 정보를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PatchMapping(value = "/api/workplaces/{workplaceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<WorkplaceDetailResponse> updateWorkplace(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "사업장 ID (Long 정수)", example = "1") @PathVariable Long workplaceId, @Valid @RequestBody WorkplaceUpdateRequest request) {
		WorkplaceDetailResponse response = workplaceService.updateWorkplace(Long.valueOf(jwt.getSubject()), workplaceId, request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
