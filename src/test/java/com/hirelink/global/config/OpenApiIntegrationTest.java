package com.hirelink.global.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hirelink.support.PostgreSQLIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String TEST_JWT_SECRET = Base64.getEncoder()
			.encodeToString("hirelink-openapi-test-secret-key".getBytes(StandardCharsets.UTF_8));

	@DynamicPropertySource
	static void securityProperties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private ObjectMapper objectMapper;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Test
	void swaggerUiAndOpenApiDocumentArePubliclyAccessible() throws Exception {
		HttpResponse<String> swaggerUi = get("/swagger-ui/index.html");
		HttpResponse<String> apiDocs = get("/v3/api-docs");

		assertThat(swaggerUi.statusCode()).isEqualTo(200);
		assertThat(swaggerUi.body()).contains("Swagger UI");
		assertThat(apiDocs.statusCode()).isEqualTo(200);
		assertThat(apiDocs.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
				.startsWith(MediaType.APPLICATION_JSON_VALUE);
	}

	@Test
	void openApiDocumentContainsMetadataBearerAuthAndExistingApiSchemas() throws Exception {
		HttpResponse<String> response = get("/v3/api-docs");
		JsonNode document = objectMapper.readTree(response.body());

		assertThat(document.path("info").path("title").asString()).isEqualTo("HIRELINK API");
		assertThat(document.path("info").path("description").asString()).isEqualTo("HIRELINK 백엔드 API 명세");
		assertThat(document.path("info").path("version").asString()).isEqualTo("0.0.1-SNAPSHOT");
		JsonNode bearerAuth = document.path("components").path("securitySchemes").path("bearerAuth");
		assertThat(bearerAuth.path("type").asString()).isEqualTo("http");
		assertThat(bearerAuth.path("scheme").asString()).isEqualTo("bearer");
		assertThat(bearerAuth.path("bearerFormat").asString()).isEqualTo("JWT");
		assertThat(document.path("security").get(0).has("bearerAuth")).isTrue();
		assertThat(document.path("paths").has("/api/auth/login")).isTrue();
		assertThat(document.path("paths").has("/api/recommendations")).isTrue();
		assertThat(document.path("components").path("schemas").has("LoginRequest")).isTrue();
		assertThat(document.path("components").path("schemas").has("LoginResponse")).isTrue();
		assertThat(response.body()).doesNotContain("JWT_SECRET", "DB_URL", TEST_JWT_SECRET);
	}

	@Test
	void recommendationAndStudentApplicationListsUseDistinctItemSchemas() throws Exception {
		JsonNode document = objectMapper.readTree(get("/v3/api-docs").body());
		JsonNode schemas = document.path("components").path("schemas");

		JsonNode recommendationItems = schemas.path("RecommendationListResponse").path("properties")
				.path("recommendations").path("items");
		JsonNode applicationItems = schemas.path("StudentApplicationListResponse").path("properties")
				.path("applications").path("items");

		assertThat(recommendationItems.path("$ref").asString())
				.isEqualTo("#/components/schemas/RecommendationListItem");
		assertThat(applicationItems.path("$ref").asString())
				.isEqualTo("#/components/schemas/StudentApplicationListItem");
		assertThat(schemas.path("RecommendationListItem").path("properties").propertyNames())
				.contains("recommendationId", "jobPostingId", "applicationId", "applicationStatus", "rank",
						"jobTitle", "workplaceName", "workplaceAddress", "wageType", "wageAmount", "workTimes",
						"matchScore", "recommendationReasons")
				.doesNotContain("desiredStartDate", "appliedAt", "offerId", "matchId", "endReason");
	}

	@Test
	void localAuthDocumentsDevelopmentAccessQaLoginExceptionAndForbiddenResponse() throws Exception {
		JsonNode paths = objectMapper.readTree(get("/v3/api-docs").body()).path("paths");
		for (String path : new String[]{"/api/auth/signup", "/api/auth/login"}) {
			JsonNode operation = paths.path(path).path("post");
			assertThat(operation.path("summary").asString()).contains("개발용");
			assertThat(operation.path("description").asString()).contains("LOCAL_LOGIN_ENABLED=true", "기본값 false");
			JsonNode forbidden = operation.path("responses").path("403").path("content").path("application/json");
			assertThat(forbidden.path("schema").path("$ref").asString()).isEqualTo("#/components/schemas/ErrorResponse");
			assertThat(forbidden.path("examples").path("FORBIDDEN").path("value").path("code").asString())
					.isEqualTo("FORBIDDEN");
		}
		assertThat(paths.has("/api/auth/refresh")).isTrue();
		assertThat(paths.has("/api/auth/logout")).isTrue();
		assertThat(paths.path("/api/auth/login").path("post").path("description").asString())
				.contains("QA_LOGIN_ALLOWED_EMAILS", "기존 LOCAL 계정", "회원가입이나 회원 유형별 권한을 변경하지 않는다");
		assertThat(paths.path("/api/auth/signup").path("post").path("description").asString())
				.doesNotContain("QA_LOGIN_ALLOWED_EMAILS");
	}

	@Test
	void openApiDocumentUsesForwardedHttpsServerUrlBehindReverseProxy() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri("/v3/api-docs"))
				.header("X-Forwarded-Proto", "https")
				.header("X-Forwarded-Host", "api.hirelink.site")
				.GET()
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode document = objectMapper.readTree(response.body());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(document.path("servers").get(0).path("url").asString())
				.isEqualTo("https://api.hirelink.site");
	}

	@Test
	void openApiDocumentStillUsesLocalServerUrlWithoutForwardedHeaders() throws Exception {
		HttpResponse<String> response = get("/v3/api-docs");
		JsonNode document = objectMapper.readTree(response.body());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(document.path("servers").get(0).path("url").asString())
				.isEqualTo("http://localhost:" + port);
	}

	@Test
	void swaggerPermissionDoesNotChangeExistingApiSecurityPolicy() throws Exception {
		HttpResponse<String> publicHealth = get("/health");
		HttpResponse<String> protectedApi = get("/api/recommendations");
		HttpRequest publicLoginRequest = HttpRequest.newBuilder(uri("/api/auth/login"))
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.POST(HttpRequest.BodyPublishers.ofString("{}"))
				.build();
		HttpResponse<String> publicLogin = httpClient.send(publicLoginRequest, HttpResponse.BodyHandlers.ofString());

		assertThat(publicHealth.statusCode()).isEqualTo(200);
		assertThat(publicLogin.statusCode()).isEqualTo(400);
		assertThat(protectedApi.statusCode()).isEqualTo(401);
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}
}
