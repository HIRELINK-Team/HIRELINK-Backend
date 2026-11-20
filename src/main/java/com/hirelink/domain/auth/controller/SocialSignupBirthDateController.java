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

	@Operation(summary = "유학생 가입 생년월일 저장 및 나이 검사", description = "등록증과 필요한 PDF 확인 후, 전화번호 인증 전에 호출한다. 필수 약관 동의와 유효한 체류기간이 필요하다. 직접 입력한 YYYY-MM-DD를 한국 날짜 기준 만 18세 이상인지 검사하고 가입 세션에 저장한다. 생일 당일부터 허용하며 미성년자·미래 날짜는 422 AGE_REQUIREMENT_NOT_MET이다. 등록번호와 생년월일을 대조하지 않는다. 가입 쿠키와 CSRF를 사용하고 계정·JWT·프로필을 생성하지 않는다. 같은 세션에서 다시 저장할 수 있고 30분 만료는 연장되지 않는다. SMS 발송과 확인도 저장된 날짜를 재검사한다. 계정 생성 시 회원 연결 정보로 이전하며 이후 온보딩은 거주지역부터 입력한다. 가입 세션 만료·재시작 시 임시 날짜도 삭제된다. LOCAL·사업주는 기존 가입 흐름을 사용한다.")
	@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답의 token", schema = @Schema(type = "string"))
	@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "나이 검사 및 저장 완료", content = @Content(schema = @Schema(implementation = SocialSignupBirthDateResponse.class))),
		@ApiResponse(responseCode = "400", description = "잘못된 날짜·추가 필드·필수 약관 미동의", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "가입 세션 없음·만료", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "유학생 아님·CSRF 오류·회원 유형 불일치·탈퇴", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "409", description = "이미 가입됨·등록증 또는 PDF 확인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "422", description = "만 18세 미만 또는 PDF 체류기간 만료", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "AGE_REQUIREMENT_NOT_MET", value = "{\"code\":\"AGE_REQUIREMENT_NOT_MET\",\"message\":\"만 18세 이상만 가입할 수 있습니다.\"}"),
			@ExampleObject(name = "STAY_DOCUMENT_EXPIRED", value = "{\"code\":\"STAY_DOCUMENT_EXPIRED\",\"message\":\"증빙서류의 체류기간이 지났습니다. 등록증 앞면부터 다시 확인해주세요.\"}") })),
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
