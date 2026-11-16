package com.hirelink.domain.auth.controller;

import java.time.Duration;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.auth.dto.SignupRequest;
import com.hirelink.domain.auth.dto.SignupResponse;
import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.dto.CsrfResponse;
import com.hirelink.domain.auth.dto.LoginRequest;
import com.hirelink.domain.auth.dto.LoginResponse;
import com.hirelink.domain.auth.dto.LogoutResponse;
import com.hirelink.domain.auth.dto.RefreshResponse;
import com.hirelink.domain.auth.service.AuthService;
import com.hirelink.domain.auth.service.TokenService;
import com.hirelink.global.exception.ErrorResponse;

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

@Tag(name = "Auth", description = "이메일 회원가입·로그인과 쿠키 기반 토큰 관리")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final TokenService tokenService;
	private final boolean secureCookie;

	public AuthController(AuthService authService, TokenService tokenService,
			@Value("${auth.cookie.secure}") boolean secureCookie) {
		this.authService = authService;
		this.tokenService = tokenService;
		this.secureCookie = secureCookie;
	}

	@Operation(summary = "이메일 회원가입",
			description = "LOCAL 이메일로 가입하고 토큰을 발급한다. 이메일은 trim·lowercase 정규화, 비밀번호는 8자 이상·영문·숫자 포함, 회원 유형은 STUDENT 또는 EMPLOYER이다. 성공 시 refreshToken을 HttpOnly 쿠키로 설정한다. Domain 미설정, Path=/api/auth, SameSite=Lax, Max-Age=1209600초(14일), Secure는 서버 설정에 따른다. Access Token 유효시간은 3600초이며 보호 API에는 Authorization: Bearer 헤더로 보낸다.")
	@SecurityRequirements
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "처리 성공",
				headers = @Header(name = "Set-Cookie", description = "refreshToken 쿠키 설정 (HttpOnly, Path=/api/auth, SameSite=Lax, 14일)", schema = @Schema(type = "string")),
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = SignupResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"userId\":1,\"email\":\"student@example.com\",\"role\":\"STUDENT\",\"accessToken\":\"access-token-example\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}"))),
		@ApiResponse(responseCode = "400", description = "입력 형식 또는 값 오류 / 비밀번호 처리 실패",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"요청 형식이 올바르지 않습니다.\"}"),
							@ExampleObject(name = "INVALID_PASSWORD", value = "{\"code\":\"INVALID_PASSWORD\",\"message\":\"비밀번호를 처리할 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "401", description = "유효한 인증 정보 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "UNAUTHORIZED", value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}")
						})),
		@ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "DUPLICATE_EMAIL", value = "{\"code\":\"DUPLICATE_EMAIL\",\"message\":\"이미 사용 중인 이메일입니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		AuthResult result = authService.signup(request);
		SignupResponse response = new SignupResponse(result.userId(), result.email(), result.role(),
				result.accessToken(), "Bearer", TokenService.ACCESS_TOKEN_TTL.toSeconds());
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), TokenService.REFRESH_TOKEN_TTL).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	@Operation(summary = "이메일 로그인",
			description = "LOCAL 이메일과 비밀번호를 확인한다. 이메일은 trim·lowercase 정규화한다. 성공 시 refreshToken을 HttpOnly 쿠키로 설정한다. Domain 미설정, Path=/api/auth, SameSite=Lax, Max-Age=1209600초(14일), Secure는 서버 설정에 따른다. Access Token 유효시간은 3600초이며 보호 API에는 Authorization: Bearer 헤더로 보낸다.")
	@SecurityRequirements
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				headers = @Header(name = "Set-Cookie", description = "refreshToken 쿠키 설정 (HttpOnly, Path=/api/auth, SameSite=Lax, 14일)", schema = @Schema(type = "string")),
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"userId\":1,\"role\":\"STUDENT\",\"accessToken\":\"access-token-example\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}"))),
		@ApiResponse(responseCode = "400", description = "입력 형식 또는 값 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"요청 형식이 올바르지 않습니다.\"}")
						})),
		@ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치 / 유효한 인증 정보 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_CREDENTIALS", value = "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"이메일 또는 비밀번호가 일치하지 않습니다.\"}"),
							@ExampleObject(name = "UNAUTHORIZED", value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}")
						})),
		@ApiResponse(responseCode = "403", description = "탈퇴한 계정",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		AuthResult result = authService.login(request);
		LoginResponse response = new LoginResponse(result.userId(), result.role(), result.accessToken(),
				"Bearer", TokenService.ACCESS_TOKEN_TTL.toSeconds());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), TokenService.REFRESH_TOKEN_TTL).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	@Operation(summary = "CSRF 확인값 조회",
			description = "인증 없이 호출할 수 있다. 응답의 headerName과 token을 사용해 재발급·로그아웃 요청을 보낸다. XSRF-TOKEN 쿠키는 브라우저가 저장·전송하며, JavaScript는 쿠키 대신 응답 본문의 token을 읽는다. 쿠키가 유지되도록 fetch credentials 또는 Axios withCredentials를 설정한다.")
	@SecurityRequirements
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				headers = @Header(name = "Set-Cookie", description = "XSRF-TOKEN 쿠키 설정", schema = @Schema(type = "string")),
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = CsrfResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"headerName\":\"X-XSRF-TOKEN\",\"token\":\"csrf-token-example\"}"))),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@GetMapping("/csrf")
	public ResponseEntity<CsrfResponse> csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken()));
	}

	@Operation(summary = "Access Token 재발급",
			description = "GET /api/auth/csrf에서 받은 token을 X-XSRF-TOKEN 헤더에 넣고, 같은 브라우저의 XSRF-TOKEN 쿠키와 함께 요청한다. Access Token은 필요 없으며 요청 본문은 없다. refreshToken 쿠키가 필수다. Access Token만 새로 발급하고 Refresh Token과 14일 만료 시각은 갱신하지 않는다.")
	@SecurityRequirements
	@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
			description = "GET /api/auth/csrf 응답의 token", schema = @Schema(type = "string"))
	@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true,
			description = "CSRF 조회 시 설정된 쿠키. 브라우저가 자동 전송", schema = @Schema(type = "string"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = RefreshResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"accessToken\":\"access-token-example\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}"))),
		@ApiResponse(responseCode = "401", description = "Refresh Token 누락·만료·무효",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_REFRESH_TOKEN", value = "{\"code\":\"INVALID_REFRESH_TOKEN\",\"message\":\"유효한 Refresh Token이 필요합니다.\"}")
						})),
		@ApiResponse(responseCode = "403", description = "CSRF 확인값 누락 또는 불일치 / 탈퇴한 계정",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_CSRF_TOKEN", value = "{\"code\":\"INVALID_CSRF_TOKEN\",\"message\":\"CSRF 보안 확인값이 없거나 올바르지 않습니다.\"}"),
							@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping("/refresh")
	public ResponseEntity<RefreshResponse> refresh(
			@Parameter(name = "refreshToken", in = ParameterIn.COOKIE, required = true, description = "로그인·가입 시 받은 HttpOnly 쿠키. 재발급에 필수", schema = @Schema(type = "string"))
			@CookieValue(name = "refreshToken", required = false) String refreshToken) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(tokenService.refreshAccessToken(refreshToken));
	}

	@Operation(summary = "로그아웃",
			description = "GET /api/auth/csrf에서 받은 token을 X-XSRF-TOKEN 헤더에 넣고, 같은 브라우저의 XSRF-TOKEN 쿠키와 함께 요청한다. Access Token은 필요 없으며 요청 본문은 없다. 현재 refreshToken을 무효화하고 해당 쿠키를 Max-Age=0으로 지운다. CSRF 검사를 통과하면 Refresh Token이 없거나 이미 무효여도 200이다. 다른 로그인 세션은 유지되며, 발급된 Access Token은 만료 전까지 유효하므로 클라이언트에서도 삭제한다.")
	@SecurityRequirements
	@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
			description = "GET /api/auth/csrf 응답의 token", schema = @Schema(type = "string"))
	@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true,
			description = "CSRF 조회 시 설정된 쿠키. 브라우저가 자동 전송", schema = @Schema(type = "string"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				headers = @Header(name = "Set-Cookie", description = "refreshToken 삭제 (Max-Age=0)", schema = @Schema(type = "string")),
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = LogoutResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"message\":\"로그아웃되었습니다.\"}"))),
		@ApiResponse(responseCode = "403", description = "CSRF 확인값 누락 또는 불일치",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_CSRF_TOKEN", value = "{\"code\":\"INVALID_CSRF_TOKEN\",\"message\":\"CSRF 보안 확인값이 없거나 올바르지 않습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping("/logout")
	public ResponseEntity<LogoutResponse> logout(
			@Parameter(name = "refreshToken", in = ParameterIn.COOKIE, description = "로그인·가입 시 받은 HttpOnly 쿠키. 재발급에는 필수, 로그아웃에는 선택", schema = @Schema(type = "string"))
			@CookieValue(name = "refreshToken", required = false) String refreshToken) {
		tokenService.logout(refreshToken);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new LogoutResponse("로그아웃되었습니다."));
	}

	private ResponseCookie refreshCookie(String token, Duration maxAge) {
		return ResponseCookie.from("refreshToken", token)
				.httpOnly(true)
				.secure(secureCookie)
				.sameSite("Lax")
				.path("/api/auth")
				.maxAge(maxAge)
				.build();
	}
}
