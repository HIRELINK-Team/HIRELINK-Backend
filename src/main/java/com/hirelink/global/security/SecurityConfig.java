package com.hirelink.global.security;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errorHandler,
			@Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
			@Value("${auth.cookie.secure}") boolean secureCookie) throws Exception {
		PathPatternRequestMatcher refreshRequest = PathPatternRequestMatcher.withDefaults()
				.matcher(HttpMethod.POST, "/api/auth/refresh");
		PathPatternRequestMatcher logoutRequest = PathPatternRequestMatcher.withDefaults()
				.matcher(HttpMethod.POST, "/api/auth/logout");
		OrRequestMatcher cookieAuthenticatedRequests = new OrRequestMatcher(refreshRequest, logoutRequest);
		PathPatternRequestMatcher csrfRequest = PathPatternRequestMatcher.withDefaults()
				.matcher(HttpMethod.GET, "/api/auth/csrf");
		CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
		csrfTokenRepository.setCookieCustomizer(cookie -> cookie
				.httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/auth"));
		DefaultBearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();

		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		authoritiesConverter.setAuthoritiesClaimName("role");
		authoritiesConverter.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
		authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		http
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
						.requestMatchers(cookieAuthenticatedRequests, csrfRequest).permitAll()
						.anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.requireCsrfProtectionMatcher(cookieAuthenticatedRequests))
				.oauth2ResourceServer(resourceServer -> resourceServer
						// 쿠키로 인증하는 요청은 Bearer 헤더가 있어도 CSRF 검사를 수행한다.
						.bearerTokenResolver(request -> cookieAuthenticatedRequests.matches(request) || csrfRequest.matches(request)
								? null : bearerTokenResolver.resolve(request))
						.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter))
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);
		return http.build();
	}
}
