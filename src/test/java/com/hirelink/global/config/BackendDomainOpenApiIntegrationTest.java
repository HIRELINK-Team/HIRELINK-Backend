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
class BackendDomainOpenApiIntegrationTest extends PostgreSQLIntegrationTest {

	private static final List<OperationSpec> OPERATIONS = List.of(
			new OperationSpec("/api/auth/signup", "post", "201", "400", "401", "409", "500"),
			new OperationSpec("/api/auth/login", "post", "200", "400", "401", "403", "500"),
			new OperationSpec("/api/auth/csrf", "get", "200", "500"),
			new OperationSpec("/api/auth/refresh", "post", "200", "401", "403", "500"),
			new OperationSpec("/api/auth/logout", "post", "200", "403", "500"),
			new OperationSpec("/api/auth/social/result", "post", "200", "401", "403", "503", "500"),
			new OperationSpec("/api/auth/social/terms", "get", "200", "401", "403", "409", "503", "500"),
			new OperationSpec("/api/auth/social/terms", "put", "200", "400", "401", "403", "404", "409", "503", "500"),
			new OperationSpec("/api/auth/social/phone-verifications", "post", "200", "400", "401", "403", "409", "429", "500", "503"),
			new OperationSpec("/api/auth/social/phone-verifications/verify", "post", "201", "400", "401", "403", "409", "429", "500", "503"),
			new OperationSpec("/api/auth/phone-verifications", "post", "200", "400", "401", "403", "429", "500"),
			new OperationSpec("/api/auth/phone-verifications/verify", "post", "200", "400", "401", "403", "409", "429", "500"),
			new OperationSpec("/api/users/me/term-agreements", "put", "200", "400", "401", "403", "404", "500"),
			new OperationSpec("/api/students/me/profile", "post", "201", "400", "401", "403", "409", "500"),
			new OperationSpec("/api/students/me/profile", "get", "200", "401", "403", "404", "500"),
			new OperationSpec("/api/students/me/profile", "patch", "200", "400", "401", "403", "404", "409", "500"),
			new OperationSpec("/api/students/me/careers", "post", "400", "401", "403", "500"),
			new OperationSpec("/api/students/me/careers/{careerId}", "patch", "400", "401", "403", "500"),
			new OperationSpec("/api/students/me/careers/{careerId}", "delete", "400", "401", "403", "500"),
			new OperationSpec("/api/students/me/matching-preferences", "get", "200", "401", "403", "404", "500"),
			new OperationSpec("/api/students/me/matching-preferences", "patch", "200", "400", "401", "403", "404", "500"),
			new OperationSpec("/api/employers/me/profile", "post", "201", "400", "401", "403", "409", "500"),
			new OperationSpec("/api/employers/me/profile", "get", "200", "401", "403", "404", "500"),
			new OperationSpec("/api/employers/me/profile", "patch", "200", "400", "401", "403", "404", "500"),
			new OperationSpec("/api/workplaces/business-registration/verify", "post", "200", "400", "401", "403", "500", "502"),
			new OperationSpec("/api/workplaces/business-registration/ocr", "post", "200", "400", "401", "403", "413", "415", "422", "500", "502"),
			new OperationSpec("/api/workplaces", "post", "201", "400", "401", "403", "404", "409", "500", "502"),
			new OperationSpec("/api/employers/me/workplaces", "get", "200", "401", "403", "500"),
			new OperationSpec("/api/workplaces/{workplaceId}", "get", "200", "400", "401", "403", "404", "500"),
			new OperationSpec("/api/workplaces/{workplaceId}", "patch", "200", "400", "401", "403", "404", "500"),
			new OperationSpec("/api/job-postings", "post", "201", "400", "401", "403", "404", "500"),
			new OperationSpec("/api/employers/me/job-postings", "get", "200", "400", "401", "403", "500"),
			new OperationSpec("/api/job-postings/{jobPostingId}", "get", "200", "400", "401", "403", "404", "500"),
			new OperationSpec("/api/job-postings/{jobPostingId}", "patch", "200", "400", "401", "403", "404", "409", "500"),
			new OperationSpec("/api/job-postings/{jobPostingId}/close", "patch", "200", "400", "401", "403", "404", "409", "500"));

	@DynamicPropertySource
	static void securityProperties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder()
				.encodeToString("hirelink-backend-openapi-test-secret".getBytes(StandardCharsets.UTF_8)));
	}

	@LocalServerPort
	private int port;

	@Autowired
	private ObjectMapper objectMapper;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Test
	void operationsExposeDescriptionsAndActualResponseExamples() throws Exception {
		JsonNode document = openApiDocument();
		for (OperationSpec spec : OPERATIONS) {
			JsonNode operation = operation(document, spec.path(), spec.method());
			assertThat(operation.path("summary").asString()).as(spec.path()).isNotBlank();
			assertThat(operation.path("description").asString()).as(spec.path()).isNotBlank();
			assertThat(operation.path("tags").isEmpty()).as(spec.path()).isFalse();
			for (String status : spec.statuses()) {
				JsonNode response = operation.path("responses").path(status);
				assertThat(response.path("description").asString()).as(spec.path() + " " + status).isNotBlank();
				if (status.equals("204")) {
					assertThat(response.path("content").isMissingNode()).isTrue();
					continue;
				}
				JsonNode content = response.path("content").path("application/json");
				assertThat(content.path("schema").path("$ref").asString()).as(spec.path() + " " + status).isNotBlank();
				assertThat(content.path("examples").isEmpty()).as(spec.path() + " " + status).isFalse();
				for (JsonNode example : content.path("examples")) {
					JsonNode value = example.path("value");
					assertThat(value.isObject()).as(spec.path() + " " + status).isTrue();
					JsonNode properties = resolve(document, content.path("schema")).path("properties");
					assertThat(value.propertyNames()).containsExactlyInAnyOrderElementsOf(properties.propertyNames());
					if (!status.startsWith("2")) {
						assertThat(value.path("code").asString()).isNotBlank();
						assertThat(value.path("message").asString()).isNotBlank();
					}
				}
			}
		}
	}

	@Test
	void authOperationsSeparateBearerFromCsrfAndRefreshCookies() throws Exception {
		JsonNode document = openApiDocument();
		for (String endpoint : List.of("signup", "login", "csrf", "refresh", "logout", "social/result")) {
			JsonNode auth = operation(document, "/api/auth/" + endpoint, endpoint.equals("csrf") ? "get" : "post");
			assertThat(auth.has("security")).as(endpoint).isTrue();
			assertThat(auth.path("security").isEmpty()).as(endpoint).isTrue();
		}
		assertThat(document.path("security").get(0).has("bearerAuth")).isTrue();
		for (String endpoint : List.of("refresh", "logout")) {
			JsonNode auth = operation(document, "/api/auth/" + endpoint, "post");
			assertThat(auth.has("requestBody")).isFalse();
			assertThat(parameter(auth, "X-XSRF-TOKEN").path("required").asBoolean()).isTrue();
			assertThat(parameter(auth, "X-XSRF-TOKEN").path("in").asString()).isEqualTo("header");
			assertThat(parameter(auth, "XSRF-TOKEN").path("in").asString()).isEqualTo("cookie");
			assertThat(parameter(auth, "refreshToken").path("in").asString()).isEqualTo("cookie");
		}
		assertThat(parameter(operation(document, "/api/auth/refresh", "post"), "refreshToken")
				.path("required").asBoolean()).isTrue();
		assertThat(operation(document, "/api/auth/logout", "post").path("responses").has("401")).isFalse();
		assertThat(operation(document, "/api/auth/csrf", "get").path("parameters").isEmpty()).isTrue();
	}

	@Test
	void socialAuthDocumentsRedirectAndTemporarySignupCookie() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode start = operation(document, "/api/auth/social/{provider}", "get");
		assertThat(start.path("security").isEmpty()).isTrue();
		assertThat(start.path("responses").path("302").path("headers").has("Location")).isTrue();
		assertThat(contains(parameter(start, "provider").path("schema").path("enum"), "google")).isTrue();
		assertThat(contains(parameter(start, "provider").path("schema").path("enum"), "kakao")).isTrue();
		assertThat(parameter(start, "role").path("required").asBoolean()).isTrue();
		assertThat(contains(parameter(start, "role").path("schema").path("enum"), "EMPLOYER")).isTrue();
		JsonNode result = operation(document, "/api/auth/social/result", "post");
		assertThat(result.has("requestBody")).isFalse();
		for (String name : List.of("X-XSRF-TOKEN", "XSRF-TOKEN", "socialSignup")) {
			assertThat(parameter(result, name).path("required").asBoolean()).as(name).isTrue();
		}
		assertThat(parameter(result, "socialSignup").path("in").asString()).isEqualTo("cookie");
		JsonNode fields = schema(document, "SocialAuthResponse").path("properties");
		assertThat(fields.propertyNames()).containsExactlyInAnyOrder("status", "role", "email", "signupExpiresAt", "login");
		assertThat(contains(fields.path("status").path("enum"), "SIGNUP_REQUIRED")).isTrue();
		assertThat(nullable(fields.path("email"))).isTrue();
		assertThat(nullable(fields.path("signupExpiresAt"))).isTrue();
		assertThat(nullable(fields.path("login"))).isTrue();
		for (String name : List.of("StudentProfileResponse", "StudentProfileUpdateResponse",
				"EmployerProfileResponse", "EmployerProfileCreateResponse")) {
			assertThat(nullable(schema(document, name).path("properties").path("email"))).as(name).isTrue();
		}
	}

	@Test
	void socialSignupTermsDocumentCookieCsrfAndConsentFields() throws Exception {
		JsonNode document = openApiDocument();
		for (String method : List.of("get", "put")) {
			JsonNode terms = operation(document, "/api/auth/social/terms", method);
			assertThat(terms.path("security").isEmpty()).isTrue();
			assertThat(parameter(terms, "socialSignup").path("required").asBoolean()).isTrue();
			assertThat(parameter(terms, "socialSignup").path("in").asString()).isEqualTo("cookie");
		}
		JsonNode update = operation(document, "/api/auth/social/terms", "put");
		for (String name : List.of("X-XSRF-TOKEN", "XSRF-TOKEN")) {
			assertThat(parameter(update, name).path("required").asBoolean()).as(name).isTrue();
		}
		JsonNode agreement = schema(document, "SocialSignupTermsRequestAgreement");
		assertThat(contains(agreement.path("required"), "termTypeId")).isTrue();
		assertThat(contains(agreement.path("required"), "isAgreed")).isTrue();
		JsonNode fields = schema(document, "SocialSignupTerm").path("properties");
		assertThat(fields.propertyNames()).containsExactlyInAnyOrder(
				"termTypeId", "termTypeName", "isRequired", "isAgreed", "agreedAt");
		assertThat(nullable(fields.path("agreedAt"))).isTrue();
	}

	@Test
	void socialSignupPhoneDocumentsCookiesExpiryDuplicateCleanupAndOnboarding() throws Exception {
		JsonNode document = openApiDocument();
		for (String path : List.of("/api/auth/social/phone-verifications", "/api/auth/social/phone-verifications/verify")) {
			JsonNode operation = operation(document, path, "post");
			assertThat(operation.path("security").isEmpty()).isTrue();
			for (String name : List.of("socialSignup", "XSRF-TOKEN", "X-XSRF-TOKEN")) {
				assertThat(parameter(operation, name).path("required").asBoolean()).as(path + name).isTrue();
			}
			assertThat(parameter(operation, "socialSignup").path("in").asString()).isEqualTo("cookie");
		}
		JsonNode request = operation(document, "/api/auth/social/phone-verifications", "post");
		assertThat(request.path("description").asString()).contains("300초", "30분", "즉시 삭제되지", "60초");
		JsonNode confirm = operation(document, "/api/auth/social/phone-verifications/verify", "post");
		assertThat(confirm.path("description").asString()).contains("온보딩 완료가 아니다", "기존 회원·소셜 연결·로그인 토큰", "동의 시각 그대로");
		assertThat(confirm.path("responses").path("409").path("headers").has("Set-Cookie")).isTrue();
		assertThat(confirm.path("responses").path("400").path("content").path("application/json").path("examples")
				.has("INVALID_VERIFICATION_CODE")).isTrue();
		assertThat(schema(document, "SocialSignupResponse").path("properties").propertyNames())
				.containsExactlyInAnyOrder("userId", "role", "accessToken", "tokenType", "expiresIn", "onboardingRequired");
	}

	@Test
	void patchSchemasPreserveOmittedFieldsAndDocumentOnlySupportedFields() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode student = schema(document, "StudentInformationUpdateRequest");
		assertThat(student.path("required").isEmpty()).isTrue();
		assertThat(student.path("properties").propertyNames()).containsExactlyInAnyOrder(
				"birthDate", "phoneNumber", "currentRegion", "koreanLevel", "preferredLocation", "careers");
		assertThat(student.path("properties").has("topikLevel")).isFalse();
		assertThat(nullable(student.path("properties").path("currentRegion"))).isFalse();
		assertType(student.path("properties").path("currentRegion"), "string");
		assertThat(student.path("properties").path("birthDate").path("format").asString()).isEqualTo("date");
		assertThat(schema(document, "StudentProfileResponse").path("properties").propertyNames())
				.contains("studyProgramId", "studyProgram", "gradeLevel", "profileImageUrl", "careers");
		assertThat(schema(document, "StudentProfileUpdateResponse").path("properties").propertyNames())
				.contains("studyProgramId", "studyProgram", "gradeLevel").doesNotContain("profileImageUrl", "careers");
		JsonNode employer = schema(document, "EmployerProfileUpdateRequest");
		assertThat(employer.path("properties").propertyNames()).containsExactlyInAnyOrder("name", "position");
		assertThat(nullable(employer.path("properties").path("position"))).isTrue();
		assertThat(schema(document, "WorkplaceUpdateRequest").path("properties").propertyNames())
				.containsExactlyInAnyOrder("workplaceName", "businessTypeId", "workplaceAddress", "latitude", "longitude");
	}

	@Test
	void careerSchemasRequireDescriptionForWritesAndPreserveLegacyReadNullability() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode create = schema(document, "StudentCareerCreateRequest");
		assertThat(contains(create.path("required"), "description")).isTrue();
		assertThat(nullable(create.path("properties").path("description"))).isFalse();
		JsonNode career = resolve(document, schema(document, "StudentProfileResponse").path("properties")
				.path("careers").path("items"));
		assertThat(nullable(career.path("properties").path("description"))).isTrue();
		for (String path : List.of("/api/students/me/careers", "/api/students/me/careers/{careerId}")) {
			JsonNode operation = operation(document, path, path.endsWith("{careerId}") ? "patch" : "post");
			assertThat(operation.path("deprecated").asBoolean()).isTrue();
			assertThat(operation.path("description").asString()).contains("DB를 변경하지", "일괄 저장");
			assertThat(operation.has("requestBody")).isFalse();
		}
	}

	@Test
	void studentProfileDocumentsFinalBatchSaveAndImmutableDocumentFields() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode request = schema(document, "StudentInformationUpdateRequest");
		assertThat(request.path("properties").propertyNames()).doesNotContain("visaCode", "stayExpirationDate", "topikLevel", "academicState");
		String description = operation(document, "/api/students/me/profile", "patch").path("description").asString();
		assertThat(description).contains("수정 반영하기", "전체 롤백", "5분", "만 18세", "임시 변경");
		assertThat(operation(document, "/api/auth/phone-verifications/verify", "post").path("description").asString())
				.contains("5분", "변경하지 않는다");
	}

	@Test
	void matchingPreferenceNestedRequestAndResponseSchemasDoNotCollide() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode request = schema(document, "StudentMatchingPreferenceUpdateRequest");
		JsonNode response = schema(document, "StudentMatchingPreferenceResponse");
		assertThat(request.path("required").isEmpty()).isTrue();
		assertType(request.path("properties").path("businessTypeFlexible"), "boolean");
		assertType(request.path("properties").path("preferredJobs"), "array");
		JsonNode inputJob = resolve(document, request.path("properties").path("preferredJobs").path("items"));
		JsonNode outputJob = resolve(document, response.path("properties").path("preferredJobs").path("items"));
		assertThat(inputJob.path("properties").propertyNames()).containsExactlyInAnyOrder("businessTypeId", "otherJobName");
		assertThat(outputJob.path("properties").propertyNames()).containsExactlyInAnyOrder("businessTypeId", "businessTypeName", "otherJobName");
		assertThat(nullable(request.path("properties").path("minimumHourlyWage"))).isTrue();
		JsonNode outputLocation = resolve(document, response.path("properties").path("preferredLocation"));
		assertThat(nullable(outputLocation)).isTrue();
		assertType(outputLocation, "object");
		assertThat(outputLocation.path("properties").propertyNames())
				.containsExactlyInAnyOrder("locationName", "latitude", "longitude", "radiusM");
		JsonNode location = resolve(document, request.path("properties").path("preferredLocation"));
		assertThat(location.path("required").size()).isEqualTo(4);
		assertType(location.path("properties").path("latitude"), "number");
		assertThat(schema(document, "TermAgreementRequestAgreement").path("properties").has("agreedAt")).isFalse();
		assertThat(nullable(schema(document, "TermAgreementResponseAgreement").path("properties").path("agreedAt"))).isTrue();
	}

	@Test
	void matchingRadiusConstraintsAndExamplesMatchBothWriteApisAndLegacyResponses() throws Exception {
		JsonNode document = openApiDocument();
		for (String name : List.of("StudentMatchingPreferenceUpdateRequest", "StudentOnboardingMatchingPreferenceRequest")) {
			JsonNode request = schema(document, name);
			JsonNode location = resolve(document, request.path("properties").path("preferredLocation"));
			JsonNode radius = location.path("properties").path("radiusM");
			assertType(radius, "integer");
			assertThat(contains(location.path("required"), "radiusM")).isTrue();
			assertThat(nullable(radius)).isFalse();
			assertThat(radius.path("minimum").asInt()).isEqualTo(100);
			assertThat(radius.path("maximum").asInt()).isEqualTo(1000);
			assertThat(radius.path("multipleOf").asInt()).isEqualTo(100);
			assertThat(radius.path("example").asInt()).isEqualTo(500);
		}
		JsonNode requestExample = schema(document, "StudentMatchingPreferenceUpdateRequest").path("example");
		if (requestExample.isString()) { requestExample = objectMapper.readTree(requestExample.asString()); }
		assertThat(requestExample.path("preferredLocation").path("radiusM").asInt()).isEqualTo(500);
		JsonNode outputLocation = resolve(document, schema(document, "StudentMatchingPreferenceResponse")
				.path("properties").path("preferredLocation"));
		JsonNode outputRadius = outputLocation.path("properties").path("radiusM");
		assertThat(outputRadius.has("maximum")).isFalse();
		assertThat(outputRadius.has("multipleOf")).isFalse();
		assertThat(outputRadius.path("description").asString()).contains("기존 저장값은 그대로 반환");
		for (String method : List.of("get", "patch")) {
			JsonNode example = operation(document, "/api/students/me/matching-preferences", method)
					.path("responses").path("200").path("content").path("application/json")
					.path("examples").path("success").path("value");
			if (example.isString()) { example = objectMapper.readTree(example.asString()); }
			assertThat(example.path("preferredLocation").path("radiusM").asInt()).isEqualTo(500);
		}
		assertThat(operation(document, "/api/students/me/matching-preferences", "patch").path("description").asString())
				.contains("100~1,000m", "100m 간격");
		assertThat(operation(document, "/api/students/me/onboarding", "post").path("description").asString())
				.contains("100~1,000m", "100m 간격");
	}

	@Test
	void draftPostingsAndListFiltersExposeConditionalFields() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode request = schema(document, "JobPostingCreateRequest");
		assertThat(request.path("required").size()).isEqualTo(2);
		assertThat(contains(request.path("required"), "workplaceId")).isTrue();
		assertThat(contains(request.path("required"), "status")).isTrue();
		assertType(request.path("properties").path("workplaceId"), "integer");
		assertType(request.path("properties").path("workTimes"), "array");
		assertThat(nullable(request.path("properties").path("jobRole"))).isTrue();
		JsonNode update = schema(document, "JobPostingUpdateRequest");
		assertThat(update.path("required").isEmpty()).isTrue();
		assertThat(nullable(update.path("properties").path("jobRole"))).isFalse();
		assertThat(update.path("properties").has("workplaceId")).isFalse();
		JsonNode detail = schema(document, "JobPostingDetailResponse");
		assertThat(nullable(detail.path("properties").path("businessTypeId"))).isTrue();
		assertThat(nullable(detail.path("properties").path("workTimes"))).isFalse();
		JsonNode list = operation(document, "/api/employers/me/job-postings", "get");
		JsonNode status = parameter(list, "status").path("schema").path("enum");
		assertThat(status.size()).isEqualTo(2);
		assertThat(contains(status, "RECRUITING")).isTrue();
		assertThat(contains(status, "CLOSED")).isTrue();
		assertThat(parameter(list, "includeDraft").path("description").asString()).contains("status");
		assertThat(operation(document, "/api/job-postings/{jobPostingId}/close", "patch").has("requestBody")).isFalse();
	}

	@Test
	void ocrDocumentsOneBinaryFileAndNoInternalRequestParameter() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode ocr = operation(document, "/api/workplaces/business-registration/ocr", "post");
		JsonNode body = resolve(document, ocr.path("requestBody").path("content").path("multipart/form-data").path("schema"));
		assertThat(body.path("properties").propertyNames()).containsExactly("businessRegistrationFile");
		assertThat(contains(body.path("required"), "businessRegistrationFile")).isTrue();
		assertThat(body.path("properties").path("businessRegistrationFile").path("format").asString()).isEqualTo("binary");
		assertThat(ocr.path("parameters").isEmpty()).isTrue();
		assertThat(schema(document, "BusinessRegistrationOcrResponse").path("properties").propertyNames())
				.containsExactlyInAnyOrder("businessRegistrationNumber", "workplaceAddress");
		assertThat(nullable(schema(document, "BusinessRegistrationOcrResponse").path("properties").path("workplaceAddress"))).isTrue();
	}

	@Test
	void dateTimeFormatsAndVerificationErrorCodesMatchTheContract() throws Exception {
		JsonNode document = openApiDocument();
		JsonNode month = schema(document, "StudentCareerCreateRequest").path("properties").path("startMonth");
		assertType(month, "string");
		assertThat(month.path("pattern").asString()).contains("[0-9]");
		JsonNode time = schema(document, "JobPostingWorkTimeRequest").path("properties").path("endTime");
		assertType(time, "string");
		assertThat(time.path("description").asString()).contains("00:00");
		JsonNode verification = operation(document, "/api/auth/phone-verifications/verify", "post");
		assertThat(verification.path("responses").path("409").path("content").path("application/json")
				.path("examples").has("DUPLICATE_PHONE_NUMBER")).isTrue();
		assertThat(operation(document, "/api/auth/phone-verifications", "post").path("responses").path("429")
				.path("content").path("application/json").path("examples").has("PHONE_VERIFICATION_RATE_LIMIT")).isTrue();
	}

	private JsonNode openApiDocument() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		return objectMapper.readTree(response.body());
	}

	private JsonNode operation(JsonNode document, String path, String method) {
		return document.path("paths").path(path).path(method);
	}

	private JsonNode schema(JsonNode document, String name) {
		JsonNode schema = document.path("components").path("schemas").path(name);
		assertThat(schema.isMissingNode()).as(name).isFalse();
		return schema;
	}

	private JsonNode resolve(JsonNode document, JsonNode node) {
		return node.has("$ref") ? document.at(node.path("$ref").asString().substring(1)) : node;
	}

	private JsonNode parameter(JsonNode operation, String name) {
		for (JsonNode parameter : operation.path("parameters")) {
			if (name.equals(parameter.path("name").asString())) {
				return parameter;
			}
		}
		throw new AssertionError("Missing parameter: " + name);
	}

	private boolean contains(JsonNode array, String text) {
		for (JsonNode item : array) {
			if (text.equals(item.asString())) {
				return true;
			}
		}
		return false;
	}

	private boolean nullable(JsonNode schema) {
		return schema.path("nullable").asBoolean(false) || "null".equals(schema.path("type").asString(""))
				|| contains(schema.path("type"), "null")
				|| java.util.stream.StreamSupport.stream(schema.path("oneOf").spliterator(), false)
						.anyMatch(option -> "null".equals(option.path("type").asString()));
	}

	private void assertType(JsonNode schema, String type) {
		assertThat(type.equals(schema.path("type").asString("")) || contains(schema.path("type"), type))
				.as(schema.toString()).isTrue();
	}

	private record OperationSpec(String path, String method, List<String> statuses) {
		private OperationSpec(String path, String method, String... statuses) {
			this(path, method, List.of(statuses));
		}
	}
}
