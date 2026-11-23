package com.hirelink.global.config;

import java.util.Arrays;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
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

	private static final String[] PROTECTED_PATHS = {
			"/api/students/me/profile", "/api/students/me/profile/**",
			"/api/students/me/careers", "/api/students/me/careers/**",
			"/api/students/me/matching-preferences",
			"/api/students/me/documents/**", "/api/students/me/badge",
			"/api/students/me/working-conditions", "/api/students/me/registration-renewals/**"
	};
	private final StudentOnboardingInterceptor interceptor;

	public StudentOnboardingAccessConfig(StudentOnboardingInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(interceptor).addPathPatterns(PROTECTED_PATHS);
	}

	@Bean
	public OpenApiCustomizer studentOnboardingAccessDocumentation() {
		return api -> {
			if (api.getPaths() == null) { return; }
			var matcher = new AntPathMatcher();
			api.getPaths().forEach((path, item) -> {
				if (Arrays.stream(PROTECTED_PATHS).noneMatch(pattern -> matcher.match(pattern, path))) { return; }
				item.readOperations().forEach(operation -> {
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
}
