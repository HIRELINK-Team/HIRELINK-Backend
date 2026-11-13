package com.hirelink.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	public OpenAPI hirelinkOpenAPI() {
		SecurityScheme bearerScheme = new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT");
		return new OpenAPI()
				.info(new Info()
						.title("HIRELINK API")
						.description("HIRELINK 백엔드 API 명세")
						.version("0.0.1-SNAPSHOT"))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}
}
