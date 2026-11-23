package com.hirelink.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hirelink.domain.member.dto.OnboardingStatusResponse.Status;
import com.hirelink.domain.member.service.OnboardingStatusService;
import com.hirelink.global.exception.SocialAuthException;

@Component
public class StudentOnboardingInterceptor implements HandlerInterceptor {

	private final OnboardingStatusService onboarding;

	public StudentOnboardingInterceptor(OnboardingStatusService onboarding) {
		this.onboarding = onboarding;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwt
				&& onboarding.getStatus(Long.valueOf(jwt.getToken().getSubject())).status() == Status.REQUIRED) {
			response.setHeader("Cache-Control", "no-store");
			throw new SocialAuthException(HttpStatus.FORBIDDEN, "ONBOARDING_REQUIRED",
					"생년월일부터 온보딩을 완료해주세요.");
		}
		return true;
	}
}
