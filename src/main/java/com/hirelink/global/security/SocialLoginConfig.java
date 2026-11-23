package com.hirelink.global.security;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.service.SocialAuthService;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.exception.WithdrawnAccountException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableConfigurationProperties(SocialLoginProperties.class)
public class SocialLoginConfig {

	@Configuration
	@ConditionalOnProperty(name = "auth.social.enabled", havingValue = "true")
	static class EnabledSocialLogin {

		@Bean
		ClientRegistrationRepository socialClientRegistrations(SocialLoginProperties properties) {
			validateUrl(properties.callbackBaseUrl());
			validateUrl(properties.frontendRedirectUri());
			URI callback = URI.create(properties.callbackBaseUrl());
			URI frontend = URI.create(properties.frontendRedirectUri());
			if (!List.of("", "/").contains(callback.getPath())
					|| !callback.getScheme().equals(frontend.getScheme())
					|| !callback.getHost().equals(frontend.getHost()) || callback.getPort() != frontend.getPort()) {
				throw new IllegalArgumentException("Social callback base and frontend redirect must share one origin");
			}
			if (URI.create(properties.callbackBaseUrl()).getRawQuery() != null
					|| URI.create(properties.frontendRedirectUri()).getRawQuery() != null) {
				throw new IllegalArgumentException("Social redirect URLs must not contain a query");
			}
			return new InMemoryClientRegistrationRepository(List.of(
					registration("google", properties.google(), properties.callbackBaseUrl(), "openid", "email"),
					registration("kakao", properties.kakao(), properties.callbackBaseUrl(), "openid")));
		}

		@Bean
		@Order(1)
		SecurityFilterChain socialSecurityFilterChain(HttpSecurity http, ClientRegistrationRepository clients,
				SocialLoginProperties properties, SocialAuthService service, SocialLoginCookie cookie, com.hirelink.domain.auth.service.ProviderTokenProtector protector) throws Exception {
			DefaultOAuth2AuthorizationRequestResolver delegate = new DefaultOAuth2AuthorizationRequestResolver(
					clients, "/api/auth/social/authorize");
			delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
			OAuth2AuthorizationRequestResolver resolver = new OAuth2AuthorizationRequestResolver() {
				@Override
				public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
					return withRole(delegate.resolve(request), request, protector.enabled());
				}

				@Override
				public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientId) {
					return withRole(delegate.resolve(request, clientId), request, protector.enabled());
				}
			};
			http.securityMatcher("/api/auth/social/authorize/**", "/api/auth/social/callback/**")
					.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
					.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
					.securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
					.requestCache(AbstractHttpConfigurer::disable)
					.formLogin(AbstractHttpConfigurer::disable)
					.httpBasic(AbstractHttpConfigurer::disable)
					.logout(AbstractHttpConfigurer::disable)
					.oauth2Login(oauth -> oauth
							.clientRegistrationRepository(clients)
							.authorizedClientRepository(discardProviderTokens())
							.authorizationEndpoint(endpoint -> endpoint
									.authorizationRequestResolver(resolver)
									.authorizationRequestRepository(new SocialAuthorizationRequestRepository()))
							.redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/social/callback/*"))
							.successHandler((request, response, authentication) -> {
								try {
									OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
									OidcUser user = (OidcUser) oauthToken.getPrincipal();
									MemberRole role = (MemberRole) request.getAttribute(SocialAuthorizationRequestRepository.ROLE);
									if (role == null) {
										throw new IllegalStateException("Missing authorized member role");
									}
									AuthProvider provider = AuthProvider.valueOf(oauthToken.getAuthorizedClientRegistrationId().toUpperCase(java.util.Locale.ROOT));
									String token = service.createSession(provider, user.getSubject(), role,
											Boolean.TRUE.equals(user.getEmailVerified()) ? user.getEmail() : null, (String) request.getAttribute("hirelink.revocation-token"));
									response.addHeader(HttpHeaders.SET_COOKIE, cookie.create(token).toString());
									redirect(request, response, properties.frontendRedirectUri());
								} catch (SocialAuthException exception) {
									fail(request, response, properties, cookie, exception.getCode());
								} catch (WithdrawnAccountException exception) {
									fail(request, response, properties, cookie, "WITHDRAWN_ACCOUNT");
								} catch (RuntimeException exception) {
									fail(request, response, properties, cookie, "SOCIAL_AUTH_FAILED");
								}
							})
							.failureHandler((request, response, exception) ->
									fail(request, response, properties, cookie, "SOCIAL_AUTH_FAILED")));
			return http.build();
		}

		private OAuth2AuthorizationRequest withRole(OAuth2AuthorizationRequest original, HttpServletRequest request, boolean withdrawalEnabled) {
			if (original == null) { return null; }
			MemberRole role;
			try {
				role = MemberRole.valueOf(request.getParameter("role"));
			} catch (IllegalArgumentException | NullPointerException exception) {
				throw new OAuth2AuthorizationException(new OAuth2Error("invalid_request"));
			}
			if(withdrawalEnabled && role==MemberRole.STUDENT && "google".equals(original.getAttribute("registration_id"))) {
                original=OAuth2AuthorizationRequest.from(original).additionalParameters(parameters -> { parameters.put("access_type","offline"); parameters.put("prompt","consent"); }).build();
            }
            return OAuth2AuthorizationRequest.from(original).attributes(attributes -> {
				attributes.put(SocialAuthorizationRequestRepository.ROLE, role);
				attributes.put(SocialAuthorizationRequestRepository.CREATED_AT, Instant.now());
			}).build();
		}

		private ClientRegistration registration(String id, SocialLoginProperties.Provider provider, String base, String... scopes) {
			if (provider == null || provider.clientId() == null || provider.clientId().isBlank()
					|| provider.clientSecret() == null || provider.clientSecret().isBlank()) {
				throw new IllegalArgumentException("Social login client credentials are required");
			}
			return ClientRegistration.withRegistrationId(id).clientId(provider.clientId()).clientSecret(provider.clientSecret())
					.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
					.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
					.redirectUri(base.replaceAll("/$", "") + "/api/auth/social/callback/" + id)
					.scope(scopes).authorizationUri(provider.authorizationUri()).tokenUri(provider.tokenUri())
					.jwkSetUri(provider.jwkSetUri()).issuerUri(provider.issuerUri()).clientName(id).build();
		}

		private void validateUrl(String value) {
			URI uri = value == null ? URI.create("") : URI.create(value);
			boolean local = List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost() == null ? "" : uri.getHost());
			if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
					|| !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))) {
				throw new IllegalArgumentException("Social redirect URLs require HTTPS (HTTP is allowed on localhost)");
			}
		}

		private void fail(HttpServletRequest request, HttpServletResponse response, SocialLoginProperties properties,
				SocialLoginCookie cookie, String code) throws java.io.IOException {
			response.addHeader(HttpHeaders.SET_COOKIE, cookie.clear().toString());
			redirect(request, response, properties.frontendRedirectUri() + "?error=" + code);
		}

		private void redirect(HttpServletRequest request, HttpServletResponse response, String location) throws java.io.IOException {
			if (request.getSession(false) != null) { request.getSession(false).invalidate(); }
			response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
			response.sendRedirect(location);
		}

		private OAuth2AuthorizedClientRepository discardProviderTokens() {
			return new OAuth2AuthorizedClientRepository() {
				@Override
				public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication principal, HttpServletRequest request) { return null; }
				@Override
				public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal, HttpServletRequest request, HttpServletResponse response) {
                    if("google".equals(client.getClientRegistration().getRegistrationId()) && client.getRefreshToken()!=null) {
                        request.setAttribute("hirelink.revocation-token",client.getRefreshToken().getTokenValue());
                    }
                }
				@Override
				public void removeAuthorizedClient(String id, Authentication principal, HttpServletRequest request, HttpServletResponse response) { }
			};
		}
	}
}
