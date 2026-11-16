package com.hirelink.global.security;

import java.time.Instant;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import com.hirelink.domain.auth.service.SocialAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SocialAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	static final String ROLE = "hirelink_member_role";
	static final String CREATED_AT = "hirelink_authorization_created_at";
	private final HttpSessionOAuth2AuthorizationRequestRepository delegate = new HttpSessionOAuth2AuthorizationRequestRepository();

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		return delegate.loadAuthorizationRequest(request);
	}

	@Override
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
			HttpServletRequest request, HttpServletResponse response) {
		if (authorizationRequest != null) {
			request.getSession().setMaxInactiveInterval((int) SocialAuthService.SIGNUP_TTL.toSeconds());
		}
		delegate.saveAuthorizationRequest(authorizationRequest, request, response);
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
		OAuth2AuthorizationRequest saved = delegate.removeAuthorizationRequest(request, response);
		if (saved == null) {
			return null;
		}
		Instant createdAt = saved.getAttribute(CREATED_AT);
		if (createdAt == null || !createdAt.plus(SocialAuthService.SIGNUP_TTL).isAfter(Instant.now())) {
			return null;
		}
		request.setAttribute(ROLE, saved.getAttribute(ROLE));
		return saved;
	}
}
