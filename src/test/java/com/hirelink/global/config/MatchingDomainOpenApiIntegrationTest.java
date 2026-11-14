package com.hirelink.global.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hirelink.support.PostgreSQLIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchingDomainOpenApiIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String TEST_JWT_SECRET = Base64.getEncoder()
			.encodeToString("hirelink-matching-openapi-secret".getBytes(StandardCharsets.UTF_8));

	private static final List<OperationSpec> OPERATIONS = List.of(
			new OperationSpec("/api/recommendations", "post", "Recommendations", "201", "401", "403", "409"),
			new OperationSpec("/api/recommendations/refresh", "post", "Recommendations", "200", "401", "403", "404", "409"),
			new OperationSpec("/api/recommendations", "get", "Recommendations", "200", "401", "403", "404", "409"),
			new OperationSpec("/api/recommendations/{id}", "get", "Recommendations", "200", "400", "401", "403", "404", "409"),
			new OperationSpec("/api/applications", "post", "Applications", "201", "400", "401", "403", "404", "409"),
			new OperationSpec("/api/applications/{applicationId}/withdraw", "patch", "Applications", "200", "400", "401", "403", "404", "409"),
			new OperationSpec("/api/applications/{applicationId}/reject", "patch", "Applications", "200", "400", "401", "403", "404", "409"),
			new OperationSpec("/api/employer/applications/{id}", "get", "Applications", "200", "400", "401", "403", "404"),
			new OperationSpec("/api/job-postings/{jobId}/applications", "get", "Applications", "200", "400", "401", "403", "404"),
			new OperationSpec("/api/applications/{id}", "get", "Applications", "200", "400", "401", "403", "404"),
			new OperationSpec("/api/applications/me", "get", "Applications", "200", "400", "401", "403"),
			new OperationSpec("/api/applications/{applicationId}/offers", "post", "Employment Offers", "201", "400", "401", "403", "404", "409"),
			new OperationSpec("/api/offers/{offerId}/accept", "patch", "Employment Offers", "200", "400", "401", "403", "404", "409"),
			new OperationSpec("/api/offers/{offerId}/reject", "patch", "Employment Offers", "200", "400", "401", "403", "404", "409"),
			new OperationSpec("/api/matches/{id}", "get", "Employment Matches", "200", "400", "401", "403", "404"),
			new OperationSpec("/api/matches/me", "get", "Employment Matches", "200", "400", "401", "403", "404"));

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
	void targetOperationsExposeTagsSummariesDescriptionsAndBearerAuth() throws Exception {
		JsonNode document = openApiDocument();

		for (OperationSpec spec : OPERATIONS) {
			JsonNode operation = operation(document, spec);
			assertThat(operation.path("summary").asString()).as(spec.path()).isNotBlank();
			assertThat(operation.path("description").asString()).as(spec.path()).isNotBlank();
			assertThat(containsText(operation.path("tags"), spec.tag())).as(spec.path()).isTrue();
		}
		assertThat(document.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString())
				.isEqualTo("bearer");
		assertThat(document.path("security").get(0).has("bearerAuth")).isTrue();
		for (String tag : List.of("Recommendations", "Applications", "Employment Offers", "Employment Matches")) {
			JsonNode tagNode = findByField(document.path("tags"), "name", tag);
			assertThat(tagNode.path("description").asString()).isNotBlank();
		}
	}

	@Test
	void targetOperationsExposeActualSuccessAndErrorResponses() throws Exception {
		JsonNode document = openApiDocument();

		for (OperationSpec spec : OPERATIONS) {
			JsonNode responses = operation(document, spec).path("responses");
			for (String responseCode : spec.responseCodes()) {
				JsonNode response = responses.path(responseCode);
				assertThat(response.isMissingNode()).as(spec.path() + " " + responseCode).isFalse();
				assertThat(response.path("description").asString()).as(spec.path() + " " + responseCode).isNotBlank();
				assertThat(response.path("content").path("application/json").path("schema").isMissingNode())
						.as(spec.path() + " " + responseCode).isFalse();
			}
		}
		assertSchemaReference(document, "/api/applications", "post", "requestBody", "ApplicationCreateRequest");
		assertThat(operation(document, "/api/applications", "post").path("responses").path("201")
				.path("content").path("application/json").path("schema").path("$ref").asString())
				.endsWith("/ApplicationCreateResponse");
		assertThat(operation(document, "/api/applications", "post").path("responses").path("400")
				.path("content").path("application/json").path("schema").path("$ref").asString())
				.endsWith("/ErrorResponse");
	}

	@Test
	void parametersAndSchemasExposeDescriptionsExamplesEnumsAndNullability() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode recommendationId = findByField(
				operation(document, "/api/recommendations/{id}", "get").path("parameters"), "name", "id");
		assertThat(recommendationId.path("required").asBoolean()).isTrue();
		assertThat(recommendationId.path("description").asString()).isEqualTo("추천 ID");
		assertThat(recommendationId.path("example").asLong()).isEqualTo(101L);

		JsonNode status = findByField(
				operation(document, "/api/job-postings/{jobId}/applications", "get").path("parameters"), "name", "status");
		assertThat(status.path("required").asBoolean()).isFalse();
		assertThat(status.path("description").asString()).contains("WAITING_RESPONSE", "ENDED");
		assertThat(containsText(status.path("schema").path("enum"), "COMPLETED")).isTrue();

		JsonNode applicationRequest = document.path("components").path("schemas").path("ApplicationCreateRequest");
		assertThat(containsText(applicationRequest.path("required"), "recommendationId")).isTrue();
		assertThat(containsText(applicationRequest.path("required"), "desiredStartDate")).isTrue();
		assertThat(applicationRequest.path("properties").path("recommendationId").path("description").asString()).isNotBlank();
		assertThat(applicationRequest.path("properties").path("recommendationId").path("example").asLong()).isEqualTo(101L);

		JsonNode applicationStatus = document.path("components").path("schemas").path("ApplicationCreateResponse")
				.path("properties").path("status");
		assertThat(containsText(applicationStatus.path("enum"), "WAITING_RESPONSE")).isTrue();
		assertThat(applicationStatus.path("description").asString()).contains("사업주 응답 대기", "채용 완료", "지원 종료");
		JsonNode offerStatus = document.path("components").path("schemas").path("EmploymentOfferCreateResponse")
				.path("properties").path("offerStatus");
		assertThat(containsText(offerStatus.path("enum"), "PENDING")).isTrue();
		assertThat(offerStatus.path("description").asString()).contains("응답 대기");

		assertNullable(document.path("components").path("schemas").path("StudentApplicationDetailResponse")
				.path("properties").path("offerId"));
		assertNullable(document.path("components").path("schemas").path("EmploymentMatchDetailResponse")
				.path("properties").path("student"));
		JsonNode errorResponse = document.path("components").path("schemas").path("ErrorResponse").path("properties");
		assertThat(errorResponse.has("code")).isTrue();
		assertThat(errorResponse.has("message")).isTrue();
	}

	@Test
	void documentationChangesDoNotChangeRuntimeAuthenticationPolicy() throws Exception {
		HttpResponse<String> apiDocs = get("/v3/api-docs");
		HttpResponse<String> protectedApi = get("/api/matches/me");

		assertThat(apiDocs.statusCode()).isEqualTo(200);
		assertThat(protectedApi.statusCode()).isEqualTo(401);
		JsonNode error = objectMapper.readTree(protectedApi.body());
		assertThat(error.path("code").asString()).isEqualTo("UNAUTHORIZED");
		assertThat(error.path("message").asString()).isEqualTo("유효한 인증 정보가 필요합니다.");
	}

	private JsonNode openApiDocument() throws Exception {
		HttpResponse<String> response = get("/v3/api-docs");
		assertThat(response.statusCode()).isEqualTo(200);
		return objectMapper.readTree(response.body());
	}

	private JsonNode operation(JsonNode document, OperationSpec spec) {
		return operation(document, spec.path(), spec.method());
	}

	private JsonNode operation(JsonNode document, String path, String method) {
		JsonNode operation = document.path("paths").path(path).path(method);
		assertThat(operation.isMissingNode()).as(path + " " + method).isFalse();
		return operation;
	}

	private void assertSchemaReference(JsonNode document, String path, String method, String node,
			String schemaName) {
		String reference = operation(document, path, method).path(node).path("content")
				.path("application/json").path("schema").path("$ref").asString();
		assertThat(reference).endsWith("/" + schemaName);
	}

	private JsonNode findByField(JsonNode array, String field, String value) {
		for (JsonNode item : array) {
			if (value.equals(item.path(field).asString())) {
				return item;
			}
		}
		throw new AssertionError(field + "=" + value + " 항목을 찾을 수 없습니다.");
	}

	private boolean containsText(JsonNode array, String value) {
		for (JsonNode item : array) {
			if (value.equals(item.asString())) {
				return true;
			}
		}
		return false;
	}

	private void assertNullable(JsonNode schema) {
		JsonNode type = schema.path("type");
		boolean nullable = schema.path("nullable").asBoolean(false)
				|| type.isString() && "null".equals(type.asString()) || containsText(type, "null");
		for (JsonNode option : schema.path("oneOf")) {
			nullable = nullable || "null".equals(option.path("type").asString());
		}
		assertThat(nullable).as(schema.toString()).isTrue();
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private record OperationSpec(String path, String method, String tag, List<String> responseCodes) {
		private OperationSpec(String path, String method, String tag, String... responseCodes) {
			this(path, method, tag, List.of(responseCodes));
		}
	}
}
