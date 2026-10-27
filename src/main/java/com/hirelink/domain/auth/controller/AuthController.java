package com.hirelink.domain.auth.controller;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.auth.dto.SignupRequest;
import com.hirelink.domain.auth.dto.SignupResponse;
import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.dto.LoginRequest;
import com.hirelink.domain.auth.dto.LoginResponse;
import com.hirelink.domain.auth.service.AuthService;
import com.hirelink.domain.auth.service.TokenService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final boolean secureCookie;

	public AuthController(AuthService authService, @Value("${auth.cookie.secure}") boolean secureCookie) {
		this.authService = authService;
		this.secureCookie = secureCookie;
	}

	@PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		AuthResult result = authService.signup(request);
		SignupResponse response = new SignupResponse(result.userId(), result.email(), result.role(),
				result.accessToken(), "Bearer", TokenService.ACCESS_TOKEN_TTL.toSeconds());
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	@PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		AuthResult result = authService.login(request);
		LoginResponse response = new LoginResponse(result.userId(), result.role(), result.accessToken(),
				"Bearer", TokenService.ACCESS_TOKEN_TTL.toSeconds());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	private ResponseCookie refreshCookie(String token) {
		return ResponseCookie.from("refreshToken", token)
				.httpOnly(true)
				.secure(secureCookie)
				.sameSite("Lax")
				.path("/api/auth")
				.maxAge(TokenService.REFRESH_TOKEN_TTL)
				.build();
	}
}
