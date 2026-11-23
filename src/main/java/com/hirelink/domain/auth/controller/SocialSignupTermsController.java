package com.hirelink.domain.auth.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.auth.dto.SocialSignupTermsRequest;
import com.hirelink.domain.auth.dto.SocialSignupTermsResponse;
import com.hirelink.domain.auth.service.SocialSignupTermsService;
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
@RequestMapping("/api/auth/social/terms")
@Tag(name = "Social Auth")
@SecurityRequirements
@ApiResponses({
	@ApiResponse(responseCode = "401", description = "가입 진행 쿠키 누락·무효·만료",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "INVALID_SOCIAL_SESSION", value = "{\"code\":\"INVALID_SOCIAL_SESSION\",\"message\":\"소셜 인증을 다시 진행해주세요.\"}"))),
	@ApiResponse(responseCode = "403", description = "가입한 회원 유형 불일치 또는 탈퇴 계정",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
				@ExampleObject(name = "SOCIAL_ROLE_MISMATCH", value = "{\"code\":\"SOCIAL_ROLE_MISMATCH\",\"message\":\"가입한 회원 유형으로 로그인해주세요.\"}"),
				@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
			})),
	@ApiResponse(responseCode = "409", description = "이미 가입된 소셜 계정으로 약관 가입 절차를 요청",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "SOCIAL_ACCOUNT_ALREADY_REGISTERED", value = "{\"code\":\"SOCIAL_ACCOUNT_ALREADY_REGISTERED\",\"message\":\"이미 가입한 소셜 계정입니다. 다시 로그인해주세요.\"}"))),
	@ApiResponse(responseCode = "503", description = "소셜 로그인 설정 전",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "SOCIAL_LOGIN_UNAVAILABLE", value = "{\"code\":\"SOCIAL_LOGIN_UNAVAILABLE\",\"message\":\"소셜 로그인 준비 중입니다.\"}"))),
	@ApiResponse(responseCode = "500", description = "서버 오류",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")))
})
public class SocialSignupTermsController {

	private final SocialSignupTermsService service;
	private final SocialLoginProperties properties;

	public SocialSignupTermsController(SocialSignupTermsService service, SocialLoginProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@Operation(summary = "소셜 가입 약관 조회", description = "소셜 인증 결과가 SIGNUP_REQUIRED인 사용자의 약관 목록과 임시 동의 상태를 반환한다. socialSignup 쿠키로 식별하며 Bearer 토큰은 사용하지 않는다. 필수 여부는 서버가 제공하고 약관 본문·번역은 프론트에서 표시한다. 등록증 확인 기능 활성화 시 유학생에게만 필수 고유식별정보 수집·이용 항목이 추가되며 일반 약관과 별도로 동의받아야 한다. 사업주와 기능 비활성 상태는 기존 목록을 유지한다. 아직 저장하지 않은 동의는 false, agreedAt은 null이다. 조회해도 가입 진행 정보의 30분 만료 시각은 연장되지 않는다. ID를 고정하지 않고 이 응답의 termTypeId로 동의를 저장한다.")
	@ApiResponse(responseCode = "200", description = "약관 ID 오름차순 목록", content = @Content(mediaType = "application/json",
			schema = @Schema(implementation = SocialSignupTermsResponse.class), examples = @ExampleObject(name = "success", value = "{\"terms\":[{\"termTypeId\":4,\"termTypeName\":\"서비스 이용약관\",\"isRequired\":true,\"isAgreed\":false,\"agreedAt\":null,\"agreedVersion\":null,\"currentVersion\":\"example-v1\"},{\"termTypeId\":5,\"termTypeName\":\"개인정보 수집·이용\",\"isRequired\":true,\"isAgreed\":false,\"agreedAt\":null,\"agreedVersion\":null,\"currentVersion\":\"example-v1\"},{\"termTypeId\":6,\"termTypeName\":\"개인정보 제3자 제공\",\"isRequired\":true,\"isAgreed\":false,\"agreedAt\":null,\"agreedVersion\":null,\"currentVersion\":\"example-v1\"},{\"termTypeId\":7,\"termTypeName\":\"마케팅 정보 수신\",\"isRequired\":false,\"isAgreed\":false,\"agreedAt\":null,\"agreedVersion\":null,\"currentVersion\":\"example-v1\"}]}")))
	@GetMapping
	public ResponseEntity<SocialSignupTermsResponse> getTerms(
			@Parameter(name = SocialLoginCookie.NAME, in = ParameterIn.COOKIE, required = true, description = "소셜 인증 후 설정된 HttpOnly 쿠키", schema = @Schema(type = "string"))
			@CookieValue(name = SocialLoginCookie.NAME, required = false) String token) {
		checkEnabled();
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.getTerms(token));
	}

	@Operation(summary = "소셜 가입 약관 동의 저장", description = "신규 사용자의 동의 목록 전체를 가입 진행 정보에 임시 저장한다. 실제로 표시한 본문 version을 함께 보내며 현재 버전과 다르면 409 TERM_VERSION_MISMATCH, 버전 미설정이면 503 TERM_VERSION_UNAVAILABLE이다. 필수 약관을 모두 true로 보내야 하며, 선택 약관은 생략하면 false가 된다. 같은 버전의 true를 다시 보내면 동의 시각을 유지하고 false로 바꾸면 null로 지운다. 빈 목록·필수 항목 누락·중복 ID 등 잘못된 입력은 400이다. socialSignup 쿠키와 CSRF 쿠키·헤더가 필요하며 Bearer 토큰은 사용하지 않는다. 회원·JWT·정식 회원 동의 기록은 아직 생성하지 않는다. 가입 진행 정보 만료 시 동의도 사용할 수 없고 정기 정리 시 함께 삭제된다. 저장해도 30분 만료 시각은 연장되지 않는다.")
	@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답의 token", schema = @Schema(type = "string"))
	@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "저장한 전체 약관 동의 상태", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SocialSignupTermsResponse.class), examples = @ExampleObject(name = "success", value = "{\"terms\":[{\"termTypeId\":4,\"termTypeName\":\"서비스 이용약관\",\"isRequired\":true,\"isAgreed\":true,\"agreedAt\":\"2026-11-16T10:00:00Z\",\"agreedVersion\":\"example-v1\",\"currentVersion\":\"example-v1\"},{\"termTypeId\":5,\"termTypeName\":\"개인정보 수집·이용\",\"isRequired\":true,\"isAgreed\":true,\"agreedAt\":\"2026-11-16T10:00:00Z\",\"agreedVersion\":\"example-v1\",\"currentVersion\":\"example-v1\"},{\"termTypeId\":6,\"termTypeName\":\"개인정보 제3자 제공\",\"isRequired\":true,\"isAgreed\":true,\"agreedAt\":\"2026-11-16T10:00:00Z\",\"agreedVersion\":\"example-v1\",\"currentVersion\":\"example-v1\"},{\"termTypeId\":7,\"termTypeName\":\"마케팅 정보 수신\",\"isRequired\":false,\"isAgreed\":false,\"agreedAt\":null,\"agreedVersion\":null,\"currentVersion\":\"example-v1\"}]}"))),
		@ApiResponse(responseCode = "409", description = "TERM_VERSION_MISMATCH", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TERM_VERSION_MISMATCH\",\"message\":\"현재 약관 본문을 확인한 후 다시 동의해주세요.\"}"))),
		@ApiResponse(responseCode = "503", description = "TERM_VERSION_UNAVAILABLE", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TERM_VERSION_UNAVAILABLE\",\"message\":\"약관 버전이 아직 준비되지 않았습니다.\"}"))),
		@ApiResponse(responseCode = "400", description = "잘못된 입력 또는 필수 약관 미동의", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "REQUIRED_TERMS_NOT_AGREED", value = "{\"code\":\"REQUIRED_TERMS_NOT_AGREED\",\"message\":\"필수 약관에 모두 동의해주세요.\"}"),
			@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"요청에 같은 약관 ID가 중복되어 있습니다.\"}")
		})),
		@ApiResponse(responseCode = "403", description = "CSRF 오류, 회원 유형 불일치 또는 탈퇴 계정", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "INVALID_CSRF_TOKEN", value = "{\"code\":\"INVALID_CSRF_TOKEN\",\"message\":\"CSRF 보안 확인값이 없거나 올바르지 않습니다.\"}"),
			@ExampleObject(name = "SOCIAL_ROLE_MISMATCH", value = "{\"code\":\"SOCIAL_ROLE_MISMATCH\",\"message\":\"가입한 회원 유형으로 로그인해주세요.\"}"),
			@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
		})),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 소셜 가입에서 사용하지 않는 약관 ID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "TERM_TYPE_NOT_FOUND", value = "{\"code\":\"TERM_TYPE_NOT_FOUND\",\"message\":\"존재하지 않는 약관 유형입니다.\"}")))
	})
	@PutMapping
	public ResponseEntity<SocialSignupTermsResponse> saveTerms(
			@Parameter(name = SocialLoginCookie.NAME, in = ParameterIn.COOKIE, required = true, description = "소셜 인증 후 설정된 HttpOnly 쿠키", schema = @Schema(type = "string"))
			@CookieValue(name = SocialLoginCookie.NAME, required = false) String token,
			@Valid @RequestBody SocialSignupTermsRequest request) {
		checkEnabled();
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.saveTerms(token, request));
	}

	private void checkEnabled() {
		if (!properties.enabled()) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "SOCIAL_LOGIN_UNAVAILABLE", "소셜 로그인 준비 중입니다.");
		}
	}
}
