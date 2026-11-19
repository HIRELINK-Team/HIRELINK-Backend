package com.hirelink.domain.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.dto.PhoneVerificationConfirmRequest;
import com.hirelink.domain.auth.dto.PhoneVerificationRequest;
import com.hirelink.domain.auth.dto.PhoneVerificationRequestResponse;
import com.hirelink.domain.auth.dto.SocialSignupResponse;
import com.hirelink.domain.auth.service.PhoneVerificationService;
import com.hirelink.domain.auth.service.SocialSignupPhoneService;
import com.hirelink.domain.auth.service.TokenService;
import com.hirelink.global.exception.ErrorResponse;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.security.SocialLoginCookie;
import com.hirelink.global.security.SocialLoginProperties;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth/social/phone-verifications")
@Tag(name = "Social Auth")
@SecurityRequirements
@ApiResponses({
	@ApiResponse(responseCode = "401", description = "가입 진행 쿠키 누락·무효·만료", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "INVALID_SOCIAL_SESSION", value = "{\"code\":\"INVALID_SOCIAL_SESSION\",\"message\":\"소셜 인증을 다시 진행해주세요.\"}"))),
	@ApiResponse(responseCode = "403", description = "CSRF 오류, 기존 소셜 계정의 유형 불일치 또는 탈퇴", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
		@ExampleObject(name = "INVALID_CSRF_TOKEN", value = "{\"code\":\"INVALID_CSRF_TOKEN\",\"message\":\"CSRF 보안 확인값이 없거나 올바르지 않습니다.\"}"),
		@ExampleObject(name = "SOCIAL_ROLE_MISMATCH", value = "{\"code\":\"SOCIAL_ROLE_MISMATCH\",\"message\":\"가입한 회원 유형으로 로그인해주세요.\"}"),
		@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
	})),
	@ApiResponse(responseCode = "429", description = "발송 제한 또는 유효한 인증의 5회 오입력 차단", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "PHONE_VERIFICATION_RATE_LIMIT", value = "{\"code\":\"PHONE_VERIFICATION_RATE_LIMIT\",\"message\":\"인증 요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.\"}"))),
	@ApiResponse(responseCode = "503", description = "소셜 로그인 비활성화", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "SOCIAL_LOGIN_UNAVAILABLE", value = "{\"code\":\"SOCIAL_LOGIN_UNAVAILABLE\",\"message\":\"소셜 로그인 준비 중입니다.\"}")))
})
public class SocialSignupPhoneController {

	private final SocialSignupPhoneService service;
	private final SocialLoginProperties properties;
	private final SocialLoginCookie cookie;
	private final boolean secureCookie;

	public SocialSignupPhoneController(SocialSignupPhoneService service, SocialLoginProperties properties,
			SocialLoginCookie cookie, @Value("${auth.cookie.secure}") boolean secureCookie) {
		this.service = service;
		this.properties = properties;
		this.cookie = cookie;
		this.secureCookie = secureCookie;
	}

	@Operation(summary = "소셜 가입 휴대전화 인증번호 요청", description = "계정 생성 전 API. socialSignup 및 CSRF 쿠키·헤더가 필요하며 Bearer 토큰은 사용하지 않는다. 필수 약관 동의를 검사하고 국내 010 번호에 6자리 인증번호 발송을 요청한다. 번호 유효시간 300초, 소셜 사용자·전화번호별 60초 재전송 간격 및 최근 1시간 최대 5회. 번호별 제한은 기존 회원 인증과 공유하며 가입 재시작·완료로 초기화하지 않는다. 발송 요청은 횟수에 포함하고 커밋 뒤 SOLAPI 실패는 발송 이력에 기록한다. 재전송 요청은 이전 번호를 무효화하지만 유효한 인증의 실패 횟수는 유지한다. 5회 오입력 후 해당 인증의 만료까지 재전송을 차단한다. 가입 진행 정보는 소셜 인증부터 30분이며 요청으로 연장되지 않는다. 브라우저를 닫아도 즉시 삭제되지 않고 만료 시 사용 불가, 정리 작업은 최초 60초 후 이전 작업 종료부터 60초 간격으로 수행한다. 가입을 다시 시작할 때는 소셜 인증부터 진행한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "400", description = "잘못된 입력 또는 필수 약관 미동의", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"010으로 시작하는 휴대전화 번호를 입력해주세요.\"}"),
			@ExampleObject(name = "REQUIRED_TERMS_NOT_AGREED", value = "{\"code\":\"REQUIRED_TERMS_NOT_AGREED\",\"message\":\"필수 약관에 모두 동의해주세요.\"}")
		})),
		@ApiResponse(responseCode = "409", description = "이미 가입된 소셜 계정", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "SOCIAL_ACCOUNT_ALREADY_REGISTERED", value = "{\"code\":\"SOCIAL_ACCOUNT_ALREADY_REGISTERED\",\"message\":\"이미 가입한 소셜 계정입니다. 다시 로그인해주세요.\"}"))),
		@ApiResponse(responseCode = "200", description = "인증번호 발송 접수", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PhoneVerificationRequestResponse.class), examples = @ExampleObject(name = "success", value = "{\"expiresIn\":300,\"message\":\"인증번호가 전송되었습니다.\"}"))),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
		}))
	})
	@Parameter(name = "socialSignup", in = ParameterIn.COOKIE, required = true, description = "소셜 인증 콜백에서 받은 HttpOnly 가입 진행 쿠키. 30분 유효", schema = @Schema(type = "string"))
	@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답의 token", schema = @Schema(type = "string"))
	@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	@PostMapping
	public ResponseEntity<PhoneVerificationRequestResponse> requestCode(
			@Parameter(hidden = true) @CookieValue(name = SocialLoginCookie.NAME, required = false) String token,
			@Valid @RequestBody PhoneVerificationRequest request) {
		checkEnabled();
		service.requestCode(token, request.normalizedPhoneNumber());
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new PhoneVerificationRequestResponse(PhoneVerificationService.CODE_TTL.toSeconds(), "인증번호가 전송되었습니다."));
	}

	@Operation(summary = "소셜 가입 인증번호 확인 및 계정 생성", description = "가입 진행 쿠키·CSRF로 인증한다. 30분 가입 정보, 필수 약관, 요청한 전화번호와 300초 인증번호를 다시 검증한다. 인증 성공 후 유학생·사업주·LOCAL·소셜 전체 회원에서 전화번호 중복을 검사한다. 중복이면 새 계정 없이 이번 가입 정보·인증번호·임시 약관과 socialSignup 쿠키만 정리하고 409로 기존 계정 로그인을 안내한다. 기존 회원·소셜 연결·로그인 토큰·refreshToken 쿠키는 변경하지 않으며 외부 Google/Kakao 연결도 해제하지 않는다. 중복이 없으면 선택했던 유형·계정·소셜 인증정보·휴대전화 인증·약관 동의를 함께 저장한다. agreedAt은 임시 동의 시각 그대로, 미동의는 false/null로 저장한다. 성공 시 가입 정보와 인증번호를 소모한다. 201은 계정 생성이며 온보딩 완료가 아니다. onboardingRequired=true를 보고 필수 온보딩으로 이동한다. 생성 후 재로그인은 기존 회원 로그인으로 처리하며 남은 온보딩 단계 조회·재개 판정·접근 제한은 후속 기능이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "계정 생성, 필수 온보딩 필요", headers = @Header(name = "Set-Cookie", description = "socialSignup 삭제 및 refreshToken 설정(HttpOnly, Secure, SameSite=Lax, Path=/api/auth, 14일)", schema = @Schema(type = "string")), content = @Content(mediaType = "application/json", schema = @Schema(implementation = SocialSignupResponse.class), examples = @ExampleObject(name = "success", value = "{\"userId\":1,\"role\":\"STUDENT\",\"accessToken\":\"access-token-example\",\"tokenType\":\"Bearer\",\"expiresIn\":3600,\"onboardingRequired\":true}"))),
		@ApiResponse(responseCode = "400", description = "입력·필수 동의 오류 또는 인증번호 누락·불일치·만료", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"6자리 인증번호를 입력해주세요.\"}"),
			@ExampleObject(name = "REQUIRED_TERMS_NOT_AGREED", value = "{\"code\":\"REQUIRED_TERMS_NOT_AGREED\",\"message\":\"필수 약관에 모두 동의해주세요.\"}"),
			@ExampleObject(name = "INVALID_VERIFICATION_CODE", value = "{\"code\":\"INVALID_VERIFICATION_CODE\",\"message\":\"인증번호가 일치하지 않거나 만료되었습니다.\"}")
		})),
		@ApiResponse(responseCode = "409", description = "전화번호 중복 또는 이미 가입된 소셜 계정", headers = @Header(name = "Set-Cookie", description = "DUPLICATE_PHONE_NUMBER일 때 socialSignup만 Max-Age=0으로 삭제. 기존 refreshToken 유지", schema = @Schema(type = "string")), content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "DUPLICATE_PHONE_NUMBER", value = "{\"code\":\"DUPLICATE_PHONE_NUMBER\",\"message\":\"이미 사용 중인 휴대전화 번호입니다. 기존 계정으로 로그인해주세요.\"}"),
			@ExampleObject(name = "SOCIAL_ACCOUNT_ALREADY_REGISTERED", value = "{\"code\":\"SOCIAL_ACCOUNT_ALREADY_REGISTERED\",\"message\":\"이미 가입한 소셜 계정입니다. 다시 로그인해주세요.\"}")
		})),
		@ApiResponse(responseCode = "500", description = "서버 오류. 계정·인증정보·동의 저장 전체 롤백", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")))
	})
	@Parameter(name = "socialSignup", in = ParameterIn.COOKIE, required = true, description = "소셜 인증 콜백에서 받은 HttpOnly 가입 진행 쿠키. 30분 유효", schema = @Schema(type = "string"))
	@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답의 token", schema = @Schema(type = "string"))
	@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	@PostMapping("/verify")
	public ResponseEntity<?> confirmCode(
			@Parameter(hidden = true) @CookieValue(name = SocialLoginCookie.NAME, required = false) String token,
			@Valid @RequestBody PhoneVerificationConfirmRequest request) {
		checkEnabled();
		var result = service.confirmCode(token, request.normalizedPhoneNumber(), request.code());
		if (result.isEmpty()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.CACHE_CONTROL, "no-store")
					.header(HttpHeaders.SET_COOKIE, cookie.clear().toString())
					.body(new ErrorResponse("DUPLICATE_PHONE_NUMBER", "이미 사용 중인 휴대전화 번호입니다. 기존 계정으로 로그인해주세요."));
		}
		AuthResult tokens = result.get();
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.SET_COOKIE, cookie.clear().toString(),
						ResponseCookie.from("refreshToken", tokens.refreshToken()).httpOnly(true).secure(secureCookie)
								.sameSite("Lax").path("/api/auth").maxAge(TokenService.REFRESH_TOKEN_TTL).build().toString())
				.body(new SocialSignupResponse(tokens.userId(), tokens.role(), tokens.accessToken(), "Bearer",
						TokenService.ACCESS_TOKEN_TTL.toSeconds(), true));
	}

	private void checkEnabled() {
		if (!properties.enabled()) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "SOCIAL_LOGIN_UNAVAILABLE", "소셜 로그인 준비 중입니다.");
		}
	}
}
