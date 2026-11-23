package com.hirelink.domain.auth.controller;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.auth.dto.SocialAuthResponse;
import com.hirelink.domain.auth.service.SocialAuthService;
import com.hirelink.domain.auth.service.TokenService;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.global.exception.ErrorResponse;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.security.SocialLoginCookie;
import com.hirelink.global.security.SocialLoginProperties;

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
@RequestMapping("/api/auth/social")
@Tag(name = "Social Auth", description = "Google/Kakao 인증 및 30분 가입 진행 정보")
@SecurityRequirements
public class SocialAuthController {

	private final SocialAuthService service;
	private final SocialLoginProperties properties;
	private final SocialLoginCookie cookie;
	private final boolean secureCookie;

	public SocialAuthController(SocialAuthService service, SocialLoginProperties properties, SocialLoginCookie cookie,
			@Value("${auth.cookie.secure}") boolean secureCookie) {
		this.service = service;
		this.properties = properties;
		this.cookie = cookie;
		this.secureCookie = secureCookie;
	}

	@Operation(summary = "소셜 로그인 시작", description = "브라우저 전체 페이지 이동으로 호출한다(fetch 호출 아님). provider와 회원 유형을 선택하면 소셜 로그인 화면으로 이동한다. 가입을 중단한 뒤 재시작할 때도 이 API부터 호출한다. 기존 socialSignup 쿠키에 해당하는 가입 정보·임시 약관·인증번호를 폐기하고 쿠키를 지운다. 브라우저 종료만으로 즉시 삭제되지는 않는다. 새 소셜 인증은 새 30분 가입 정보를 만들고 같은 미가입 소셜 사용자의 이전 가입 정보도 대체한다. 기존 회원·소셜 연결·로그인 상태는 변경하지 않는다. 인증 완료 후 서버 설정의 프론트 주소로 돌아오며, 같은 브라우저에서 CSRF 조회 후 POST /api/auth/social/result를 호출한다. 인증 실패 시 프론트 주소의 error 쿼리에 SOCIAL_AUTH_FAILED, SOCIAL_ROLE_MISMATCH 또는 WITHDRAWN_ACCOUNT가 전달된다. 토큰은 URL에 포함하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "302", description = "소셜 인증 화면으로 이동",
				headers = @Header(name = "Location", description = "서버의 소셜 인증 시작 주소", schema = @Schema(type = "string"))),
		@ApiResponse(responseCode = "400", description = "지원하지 않는 provider, 회원 유형 누락 또는 잘못된 유형",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "error", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"지원하지 않는 소셜 로그인입니다.\"}"))),
		@ApiResponse(responseCode = "503", description = "소셜 로그인 설정 전",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "error", value = "{\"code\":\"SOCIAL_LOGIN_UNAVAILABLE\",\"message\":\"소셜 로그인 준비 중입니다.\"}"))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "error", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")))
	})
	@GetMapping("/{provider}")
	public ResponseEntity<Void> start(
			@Parameter(description = "로그인 제공자", schema = @Schema(allowableValues = {"google", "kakao"})) @PathVariable String provider,
			@Parameter(description = "랜딩 페이지에서 선택한 회원 유형", schema = @Schema(allowableValues = {"STUDENT", "EMPLOYER"})) @RequestParam MemberRole role,
			@Parameter(name = "socialSignup", in = ParameterIn.COOKIE, description = "이전 가입 진행 쿠키. 있으면 이번 가입 시작 시 폐기", schema = @Schema(type = "string"))
			@CookieValue(name = SocialLoginCookie.NAME, required = false) String previousToken) {
		if (!"google".equals(provider) && !"kakao".equals(provider)) {
			throw new SocialAuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "지원하지 않는 소셜 로그인입니다.");
		}
		checkEnabled();
		service.discardSignupSession(previousToken);
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create("/api/auth/social/authorize/" + provider + "?role=" + role))
				.header(HttpHeaders.SET_COOKIE, cookie.clear().toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").build();
	}

	@Operation(summary = "소셜 인증 결과 확인", description = "본문 없음. 소셜 인증 콜백이 설정한 socialSignup 쿠키와 GET /api/auth/csrf에서 받은 확인값을 사용한다. 신규 사용자는 SIGNUP_REQUIRED이며 회원 생성·JWT 발급 없이 30분 가입 진행 정보만 유지한다. 조회해도 만료 시각은 연장되지 않는다. 약관 동의 후 등록증 기능이 활성화된 유학생은 카드·필요한 PDF 확인을 완료하고 가입용 phone-verifications/verify에서 휴대전화 인증 후 계정을 생성한다. 생성된 계정은 다시 가입하지 않고 LOGIN과 기존 로그인 형식의 login 객체, refreshToken 쿠키를 받으며 가입 진행 쿠키는 삭제된다. LOGIN은 온보딩 완료를 뜻하지 않는다. LOGIN 후 액세스 토큰으로 GET /api/users/me/onboarding을 호출하여 온보딩 상태와 nextStep을 확인한다. REQUIRED/BASIC_INFORMATION은 항상 생년월일부터 다시 입력한다. COMPLETED는 추가 온보딩 없음, NOT_APPLICABLE은 기존 유형별 흐름을 뜻한다. 화면별 초안은 복구하지 않는다. 기존 회원 결과는 한 번만 교환할 수 있다. 회원 조회는 provider+고유 ID 기준이며 이메일로 연결하지 않는다.")
	@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답의 token", schema = @Schema(type = "string"))
	@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "소셜 인증 결과",
				headers = @Header(name = "Set-Cookie", description = "기존 회원: refreshToken(HttpOnly, Secure, SameSite=Lax, Path=/api/auth, 14일) 설정 및 socialSignup 삭제. 신규 사용자는 기존 socialSignup 유지", schema = @Schema(type = "string")),
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = SocialAuthResponse.class), examples = {
					@ExampleObject(name = "신규 사용자", value = "{\"status\":\"SIGNUP_REQUIRED\",\"role\":\"STUDENT\",\"email\":null,\"signupExpiresAt\":\"2026-11-16T10:30:00Z\",\"login\":null}"),
					@ExampleObject(name = "기존 회원", value = "{\"status\":\"LOGIN\",\"role\":\"EMPLOYER\",\"email\":\"member@example.com\",\"signupExpiresAt\":null,\"login\":{\"userId\":1,\"role\":\"EMPLOYER\",\"accessToken\":\"access-token-example\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}}")
				})),
		@ApiResponse(responseCode = "401", description = "가입 진행 쿠키 누락·무효·만료·이미 교환한 결과",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "error", value = "{\"code\":\"INVALID_SOCIAL_SESSION\",\"message\":\"소셜 인증을 다시 진행해주세요.\"}"))),
		@ApiResponse(responseCode = "403", description = "CSRF 오류, 회원 유형 불일치 또는 탈퇴 계정",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
					@ExampleObject(name = "INVALID_CSRF_TOKEN", value = "{\"code\":\"INVALID_CSRF_TOKEN\",\"message\":\"CSRF 보안 확인값이 없거나 올바르지 않습니다.\"}"),
					@ExampleObject(name = "SOCIAL_ROLE_MISMATCH", value = "{\"code\":\"SOCIAL_ROLE_MISMATCH\",\"message\":\"가입한 회원 유형으로 로그인해주세요.\"}"),
					@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
				})),
		@ApiResponse(responseCode = "503", description = "소셜 로그인 설정 전", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "error", value = "{\"code\":\"SOCIAL_LOGIN_UNAVAILABLE\",\"message\":\"소셜 로그인 준비 중입니다.\"}"))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "error", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")))
	})
	@PostMapping("/result")
	public ResponseEntity<SocialAuthResponse> result(
			@Parameter(name = SocialLoginCookie.NAME, in = ParameterIn.COOKIE, required = true,
					description = "소셜 인증 후 설정된 HttpOnly 쿠키. 직접 입력하지 않음", schema = @Schema(type = "string"))
			@CookieValue(name = SocialLoginCookie.NAME, required = false) String token) {
		checkEnabled();
		SocialAuthService.Result result = service.getResult(token);
		var response = ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store");
		if (result.tokens() != null) {
			response.header(HttpHeaders.SET_COOKIE, cookie.clear().toString(),
					ResponseCookie.from("refreshToken", result.tokens().refreshToken()).httpOnly(true)
							.secure(secureCookie).sameSite("Lax").path("/api/auth")
							.maxAge(TokenService.REFRESH_TOKEN_TTL).build().toString());
		}
		return response.body(result.response());
	}

	private void checkEnabled() {
		if (!properties.enabled()) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "SOCIAL_LOGIN_UNAVAILABLE", "소셜 로그인 준비 중입니다.");
		}
	}
}
