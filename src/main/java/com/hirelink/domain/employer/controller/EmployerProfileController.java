package com.hirelink.domain.employer.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.employer.dto.EmployerProfileCreateRequest;
import com.hirelink.domain.employer.dto.EmployerProfileCreateResponse;
import com.hirelink.domain.employer.dto.EmployerProfileResponse;
import com.hirelink.domain.employer.dto.EmployerProfileUpdateRequest;
import com.hirelink.domain.employer.service.EmployerProfileService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Employer Profiles", description = "사업주 본인 프로필 관리")
@RestController
@RequestMapping("/api/employers/me/profile")
public class EmployerProfileController {

	private final EmployerProfileService employerProfileService;

	public EmployerProfileController(EmployerProfileService employerProfileService) {
		this.employerProfileService = employerProfileService;
	}

	@Operation(summary = "내 사업주 프로필 조회", description = "로그인한 사업주의 본인 프로필을 조회한다. 직책 미등록은 null이다. 유입 경로와 사업장 목록은 포함하지 않는다. 프로필 미등록은 404다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmployerProfileResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"employerId\":2,\"name\":\"김사장\",\"email\":\"employer@example.com\",\"phoneNumber\":\"010-1234-5678\",\"position\":null}"))),
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
		@ApiResponse(responseCode = "404", description = "사업주 프로필 미등록",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "EMPLOYER_PROFILE_NOT_FOUND", value = "{\"code\":\"EMPLOYER_PROFILE_NOT_FOUND\",\"message\":\"사업주 프로필이 아직 등록되지 않았습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@GetMapping
	public ResponseEntity<EmployerProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
		EmployerProfileResponse response = employerProfileService.getProfile(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "사업주 프로필 등록",
			description = "휴대전화 인증을 완료한 사업주가 이름·유입 경로를 입력하여 프로필을 최초 등록한다. 전화번호는 인증된 회원 정보에서 가져오며 직책은 null이다. 회원 정보 저장과 프로필 생성은 함께 반영된다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmployerProfileCreateResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"employerId\":2,\"name\":\"김사장\",\"email\":\"employer@example.com\",\"phoneNumber\":\"010-1234-5678\",\"position\":null,\"acquisitionChannel\":\"SEARCH\"}"))),
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
		@ApiResponse(responseCode = "409", description = "휴대전화 미인증 / 이미 등록된 사업주 프로필",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "PHONE_NOT_VERIFIED", value = "{\"code\":\"PHONE_NOT_VERIFIED\",\"message\":\"휴대전화 인증을 먼저 완료해주세요.\"}"),
							@ExampleObject(name = "DUPLICATE_EMPLOYER_PROFILE", value = "{\"code\":\"DUPLICATE_EMPLOYER_PROFILE\",\"message\":\"이미 사업주 프로필이 존재합니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<EmployerProfileCreateResponse> createProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody EmployerProfileCreateRequest request) {
		EmployerProfileCreateResponse response = employerProfileService.createProfile(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "내 사업주 프로필 수정",
			description = "보내지 않은 필드는 유지한다. 빈 요청과 수정 대상이 아닌 필드는 400이다. 이름·직책만 수정한다. 이름은 null 불가, 직책의 null은 삭제다. 둘 다 빈 문자열·공백만 입력은 불가다. 성공 응답은 조회와 같은 항목이며 이름과 직책은 함께 반영된다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmployerProfileResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"employerId\":2,\"name\":\"김사장\",\"email\":\"employer@example.com\",\"phoneNumber\":\"010-1234-5678\",\"position\":null}"))),
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
		@ApiResponse(responseCode = "404", description = "사업주 프로필 미등록",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "EMPLOYER_PROFILE_NOT_FOUND", value = "{\"code\":\"EMPLOYER_PROFILE_NOT_FOUND\",\"message\":\"사업주 프로필이 아직 등록되지 않았습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<EmployerProfileResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody EmployerProfileUpdateRequest request) {
		EmployerProfileResponse response = employerProfileService.updateProfile(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
