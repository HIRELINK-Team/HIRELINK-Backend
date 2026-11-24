package com.hirelink.global.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hirelink.global.security.StudentOnboardingInterceptor;

import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

@Configuration
public class StudentOnboardingAccessConfig implements WebMvcConfigurer {

	private static final String[] EXISTING_PROTECTED_PATHS = {
			"/api/students/me/profile", "/api/students/me/profile/**",
			"/api/students/me/careers", "/api/students/me/careers/**",
			"/api/students/me/matching-preferences",
			"/api/students/me/documents/**", "/api/students/me/badge",
			"/api/students/me/working-conditions", "/api/students/me/registration", "/api/students/me/stay-expiration-notice/**", "/api/students/me/registration-renewals/**"
	};
	private static final List<ProtectedRoute> MATCHING_SERVICE_PROTECTED_ROUTES = List.of(
			new ProtectedRoute(HttpMethod.POST, "/api/recommendations"),
			new ProtectedRoute(HttpMethod.POST, "/api/recommendations/refresh"),
			new ProtectedRoute(HttpMethod.GET, "/api/recommendations"),
			new ProtectedRoute(HttpMethod.GET, "/api/recommendations/{id}"),
			new ProtectedRoute(HttpMethod.POST, "/api/applications"),
			new ProtectedRoute(HttpMethod.GET, "/api/applications/me"),
			new ProtectedRoute(HttpMethod.GET, "/api/applications/{id}"),
			new ProtectedRoute(HttpMethod.PATCH, "/api/applications/{applicationId}/withdraw"),
			new ProtectedRoute(HttpMethod.PATCH, "/api/offers/{offerId}/accept"),
			new ProtectedRoute(HttpMethod.PATCH, "/api/offers/{offerId}/reject"),
			new ProtectedRoute(HttpMethod.GET, "/api/matches/me"),
			new ProtectedRoute(HttpMethod.GET, "/api/matches/{id}")
	);
	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
	private final StudentOnboardingInterceptor interceptor;

	public StudentOnboardingAccessConfig(StudentOnboardingInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		var registeredPaths = new LinkedHashSet<>(List.of(EXISTING_PROTECTED_PATHS));
		MATCHING_SERVICE_PROTECTED_ROUTES.stream().map(ProtectedRoute::path).forEach(registeredPaths::add);
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
				if (isProtected(request.getMethod(), request.getRequestURI())) {
					return interceptor.preHandle(request, response, handler);
				}
				return true;
			}
		}).addPathPatterns(registeredPaths.toArray(String[]::new));
	}

	@Bean
	public OpenApiCustomizer studentOnboardingAccessDocumentation() {
		return api -> {
			if (api.getPaths() == null) { return; }
			api.getPaths().forEach((path, item) -> {
				item.readOperationsMap().forEach((method, operation) -> {
					if (!isProtected(method.name(), path)) { return; }
					var responses = operation.getResponses();
					ApiResponse forbidden = responses.get("403");
					if (forbidden == null) {
						forbidden = new ApiResponse().description("온보딩 미완료");
						responses.addApiResponse("403", forbidden);
					} else {
						forbidden.setDescription(forbidden.getDescription() + " / 온보딩 미완료");
					}
					if (forbidden.getContent() == null) { forbidden.setContent(new Content()); }
					MediaType json = forbidden.getContent().get("application/json");
					if (json == null) {
						json = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));
						forbidden.getContent().addMediaType("application/json", json);
					}
					json.addExamples("ONBOARDING_REQUIRED", new Example().value(Map.of(
							"code", "ONBOARDING_REQUIRED", "message", "생년월일부터 온보딩을 완료해주세요.")));
				});
			});
		};
	}

	private static boolean isProtected(String method, String path) {
		if (List.of(EXISTING_PROTECTED_PATHS).stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
			return true;
		}
		return MATCHING_SERVICE_PROTECTED_ROUTES.stream().anyMatch(route ->
				route.method().matches(method) && PATH_MATCHER.match(route.path(), path));
	}

	private record ProtectedRoute(HttpMethod method, String path) {
	}
}
