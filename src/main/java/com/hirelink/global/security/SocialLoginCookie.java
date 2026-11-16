package com.hirelink.global.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.hirelink.domain.auth.service.SocialAuthService;

@Component
public class SocialLoginCookie {

	public static final String NAME = "socialSignup";
	private final boolean secure;

	public SocialLoginCookie(@Value("${auth.cookie.secure}") boolean secure) {
		this.secure = secure;
	}

	public ResponseCookie create(String token) { return build(token, SocialAuthService.SIGNUP_TTL); }
	public ResponseCookie clear() { return build("", Duration.ZERO); }

	private ResponseCookie build(String token, Duration maxAge) {
		return ResponseCookie.from(NAME, token).httpOnly(true).secure(secure)
				.sameSite("Lax").path("/api/auth").maxAge(maxAge).build();
	}
}
