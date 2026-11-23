package com.hirelink.domain.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hirelink.domain.auth.dto.SocialSignupBirthDateRequest;
import com.hirelink.domain.auth.dto.SocialSignupBirthDateResponse;
import com.hirelink.domain.auth.service.SocialSignupBirthDateService;
import com.hirelink.global.exception.ErrorResponse;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.security.SocialLoginCookie;
import com.hirelink.global.security.SocialLoginProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth/social/birth-date")
@Tag(name = "Social Auth")
@SecurityRequirements
public class SocialSignupBirthDateController {

	private final SocialSignupBirthDateService service;
	private final SocialLoginProperties properties;

	public SocialSignupBirthDateController(SocialSignupBirthDateService service, SocialLoginProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@Operation(summary = "가입 전 생년월일 저장 종료", deprecated = true, description = "이 경로에서는 생년월일을 저장하지 않는다. 계정 생성 후 온보딩에서 입력하고 POST /api/students/me/onboarding의 마치기 요청으로 제출한다. 유효한 유학생 가입 세션의 요청은 400 INVALID_INPUT을 반환한다. 기존 가입 쿠키와 CSRF 검사는 유지한다.")
	@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답의 token", schema = @Schema(type = "string"))
	@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	@ApiResponses({
		@ApiResponse(responseCode = "400", description = "종료된 저장 경로 또는 잘못된 입력", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT\",\"message\":\"생년월일은 계정 생성 후 온보딩의 마치기에서 제출해주세요.\"}"))),
		@ApiResponse(responseCode = "401", description = "가입 세션 없음·만료", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "유학생 아님·CSRF 오류·회원 유형 불일치·탈퇴", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "409", description = "이미 가입됨", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "503", description = "소셜 로그인 또는 등록증 기능 비활성", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PutMapping
	public ResponseEntity<SocialSignupBirthDateResponse> save(
			@Parameter(name = SocialLoginCookie.NAME, in = ParameterIn.COOKIE, required = true, description = "소셜 가입 진행 쿠키", schema = @Schema(type = "string"))
			@CookieValue(name = SocialLoginCookie.NAME, required = false) String token,
			@Valid @RequestBody SocialSignupBirthDateRequest request) {
		if (!properties.enabled()) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "SOCIAL_LOGIN_UNAVAILABLE", "소셜 로그인 준비 중입니다.");
		}
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(service.save(token, request.getBirthDate()));
	}
}
