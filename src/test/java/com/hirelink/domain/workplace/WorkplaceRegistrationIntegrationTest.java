package com.hirelink.domain.workplace;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.hirelink.domain.workplace.client.PaddleOcrClient;
import com.hirelink.domain.workplace.dto.BusinessRegistrationOcrResponse;
import com.hirelink.global.exception.UnreadableBusinessRegistrationException;
import com.hirelink.domain.workplace.client.NtsBusinessRegistrationClient;
import com.hirelink.global.exception.BusinessRegistrationLookupException;
import com.hirelink.global.exception.BusinessRegistrationOcrException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkplaceRegistrationIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=");
	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
	@TempDir
	static Path uploadDirectory;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
		registry.add("spring.servlet.multipart.location", () -> uploadDirectory.toString());
		registry.add("business-registration.paddle.url", () -> "");
		registry.add("business-registration.nts.service-key", () -> "");
	}

	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ObjectMapper mapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	@MockitoBean
	private PaddleOcrClient paddleClient;
	@MockitoBean
	private NtsBusinessRegistrationClient ntsClient;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearDatabase() {
		jdbc.execute("TRUNCATE TABLE user_account RESTART IDENTITY CASCADE");
	}

	@Test
	void readsNumberAndAddressWithoutPersistingSourceThenRegistersCorrectedValues() throws Exception {
		long userId = createUser("EMPLOYER");
		String token = token(userId);
		when(paddleClient.recognize(any(), anyString())).thenAnswer(call -> {
			assertNoUploadFiles();
			return new BusinessRegistrationOcrResponse("123-45-67890", "테스트 주소");
		});
		HttpResponse<String> response = ocr(token, PNG, "image/png");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode preview = mapper.readTree(response.body());
		assertThat(preview.propertyNames()).containsExactlyInAnyOrder("businessRegistrationNumber", "workplaceAddress");
		assertThat(preview.path("workplaceAddress").asString()).isEqualTo("테스트 주소");
		assertThat(preview.path("businessRegistrationNumber").asString()).isEqualTo("123-45-67890");
		assertNoUploadFiles();
		assertThat(workplaceCount()).isZero();
		verifyNoInteractions(ntsClient);

		when(ntsClient.isActiveBusiness("0123456789")).thenReturn(true);
		Map<String, Object> input = validRequest();
		input.put("businessRegistrationNumber", " 012-34-56789 ");
		input.put("workplaceAddress", "수정한 주소");
		response = create(token, mapper.writeValueAsString(input));
		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		JsonNode result = mapper.readTree(response.body());
		assertThat(result.propertyNames()).containsExactlyInAnyOrder("workplaceId", "workplaceName",
				"businessRegistrationNumber", "businessTypeId", "businessTypeName", "employeeCountRange", "workplaceAddress", "latitude", "longitude");
		assertThat(result.path("businessRegistrationNumber").asString()).isEqualTo("012-34-56789");
		assertThat(result.path("workplaceAddress").asString()).isEqualTo("수정한 주소");
		Map<String, Object> row = jdbc.queryForMap("select * from workplace");
		assertThat(row).containsEntry("employer_user_id", userId).containsEntry("business_registration_number", "0123456789")
				.containsEntry("business_registration_file_key", null).containsEntry("representative_name", null)
				.containsEntry("workplace_address", "수정한 주소");
		verify(ntsClient).isActiveBusiness("0123456789");
		HttpResponse<String> detail = client.send(request("/api/workplaces/" + result.path("workplaceId").asLong(), token).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(mapper.readTree(detail.body())).isEqualTo(result);
	}

	@Test
	void acceptsUnrecognizedAddressButRejectsUnrecognizedNumber() throws Exception {
		String token = token(createUser("EMPLOYER"));
		when(paddleClient.recognize(any(), anyString())).thenReturn(new BusinessRegistrationOcrResponse("123-45-67890", null));
		HttpResponse<String> response = ocr(token, PNG, "image/png");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(response.body()).path("workplaceAddress").isNull()).isTrue();
		when(paddleClient.recognize(any(), anyString())).thenThrow(new UnreadableBusinessRegistrationException());
		assertError(ocr(token, PNG, "image/png"), 422, "UNREADABLE_BUSINESS_REGISTRATION");
		assertThat(workplaceCount()).isZero();
		assertNoUploadFiles();
	}

	@Test
	void acceptsSupportedFileHeadersAndMaximumFileSizeWithoutSpoolingToDisk() throws Exception {
		String token = token(createUser("EMPLOYER"));
		when(paddleClient.recognize(any(), anyString())).thenAnswer(call -> {
			assertNoUploadFiles();
			return new BusinessRegistrationOcrResponse("123-45-67890", null);
		});
		byte[] maximum = new byte[5 * 1024 * 1024];
		System.arraycopy(PNG, 0, maximum, 0, PNG.length);
		assertThat(ocr(token, maximum, "image/png").statusCode()).isEqualTo(200);
		assertThat(ocr(token, "%PDF-1.7\nsynthetic".getBytes(StandardCharsets.US_ASCII), "application/pdf").statusCode()).isEqualTo(200);
		assertThat(ocr(token, new byte[] {(byte) 255, (byte) 216, (byte) 255}, "image/jpeg").statusCode()).isEqualTo(200);
		assertNoUploadFiles();
	}

	@Test
	void enforcesFileTypeContentSizeAndSingleFileBeforeCallingProvider() throws Exception {
		String token = token(createUser("EMPLOYER"));
		assertError(ocr(token, new byte[0], "image/png"), 400, "INVALID_INPUT");
		assertError(ocr(token, "not-an-image".getBytes(StandardCharsets.UTF_8), "image/png"), 415, "UNSUPPORTED_FILE_TYPE");
		assertError(ocr(token, PNG, "text/plain"), 415, "UNSUPPORTED_FILE_TYPE");
		assertError(ocr(token, new byte[5 * 1024 * 1024 + 1], "image/png"), 413, "FILE_TOO_LARGE");
		byte[] twoFiles = multipart(PNG, "image/png", 2);
		assertError(client.send(ocrRequest(token, twoFiles), HttpResponse.BodyHandlers.ofString()), 400, "INVALID_INPUT");
		verifyNoInteractions(paddleClient, ntsClient);
		assertNoUploadFiles();
	}

	@Test
	void providerFailureDoesNotExposeDocumentOrCreateData() throws Exception {
		String token = token(createUser("EMPLOYER"));
		when(paddleClient.recognize(any(), anyString())).thenThrow(new BusinessRegistrationOcrException());
		assertError(ocr(token, PNG, "image/png"), 502, "BUSINESS_REGISTRATION_OCR_FAILED");
		assertNoUploadFiles();
		assertThat(workplaceCount()).isZero();
	}

	@Test
	void rejectsMissingInvalidAndUnexpectedRegistrationFields() throws Exception {
		String token = token(createUser("EMPLOYER"));
		for (String field : validRequest().keySet()) {
			Map<String, Object> input = validRequest();
			input.remove(field);
			assertError(create(token, mapper.writeValueAsString(input)), 400, "INVALID_INPUT");
			input.put(field, null);
			assertError(create(token, mapper.writeValueAsString(input)), 400, "INVALID_INPUT");
		}
		for (Map.Entry<String, Object> invalid : List.<Map.Entry<String, Object>>of(
				Map.entry("workplaceName", " "), Map.entry("workplaceName", "가".repeat(151)),
				Map.entry("representativeName", "테스트"),
				Map.entry("businessRegistrationNumber", "123"), Map.entry("businessRegistrationNumber", 1234567890),
				Map.entry("businessTypeId", "1"), Map.entry("businessTypeId", 1.5), Map.entry("employeeCountRange", "5"),
				Map.entry("workplaceAddress", "가".repeat(501)), Map.entry("latitude", 91),
				Map.entry("longitude", -181), Map.entry("latitude", 37.12345678), Map.entry("longitude", "127"),
				Map.entry("employerUserId", 1), Map.entry("businessRegistrationFileKey", "path"))) {
			Map<String, Object> input = validRequest();
			input.put(invalid.getKey(), invalid.getValue());
			assertError(create(token, mapper.writeValueAsString(input)), 400, "INVALID_INPUT");
		}
		verifyNoInteractions(ntsClient);
		assertThat(workplaceCount()).isZero();
	}

	@Test
	void rejectsMissingBusinessTypeInactiveBusinessAndUnavailableNtsWithoutSaving() throws Exception {
		String token = token(createUser("EMPLOYER"));
		Map<String, Object> input = validRequest();
		input.put("businessTypeId", 999);
		assertError(create(token, mapper.writeValueAsString(input)), 404, "BUSINESS_TYPE_NOT_FOUND");
		verifyNoInteractions(ntsClient);
		assertError(create(token, mapper.writeValueAsString(validRequest())), 400, "INVALID_INPUT");
		when(ntsClient.isActiveBusiness(anyString())).thenThrow(new BusinessRegistrationLookupException());
		assertError(create(token, mapper.writeValueAsString(validRequest())), 502, "BUSINESS_REGISTRATION_LOOKUP_FAILED");
		assertThat(workplaceCount()).isZero();
	}

	@Test
	void requiresCurrentEmployerAccountForBothEndpoints() throws Exception {
		long student = createUser("STUDENT");
		long withdrawn = createUser("EMPLOYER");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", withdrawn);
		String[] tokens = {null, "invalid", token(999), token(student), token(withdrawn)};
		int[] statuses = {401, 401, 401, 403, 403};
		String[] codes = {"UNAUTHORIZED", "UNAUTHORIZED", "UNAUTHORIZED", "FORBIDDEN", "WITHDRAWN_ACCOUNT"};
		for (int i = 0; i < tokens.length; i++) {
			assertError(create(tokens[i], mapper.writeValueAsString(validRequest())), statuses[i], codes[i]);
			assertError(ocr(tokens[i], PNG, "image/png"), statuses[i], codes[i]);
		}
		verifyNoInteractions(ntsClient, paddleClient);
	}

	@Test
	void duplicateRegistrationIsConflictIncludingConcurrentRequests() throws Exception {
		String firstToken = token(createUser("EMPLOYER"));
		String secondToken = token(createUser("EMPLOYER"));
		String json = mapper.writeValueAsString(validRequest());
		CyclicBarrier barrier = new CyclicBarrier(2);
		when(ntsClient.isActiveBusiness(anyString())).thenAnswer(call -> {
			barrier.await(10, TimeUnit.SECONDS);
			return true;
		});
		var first = client.sendAsync(createRequest(firstToken, json), HttpResponse.BodyHandlers.ofString());
		var second = client.sendAsync(createRequest(secondToken, json), HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> a = first.get(15, TimeUnit.SECONDS);
		HttpResponse<String> b = second.get(15, TimeUnit.SECONDS);
		assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(201, 409);
		assertError(a.statusCode() == 409 ? a : b, 409, "DUPLICATE_BUSINESS_REGISTRATION_NUMBER");
		reset(ntsClient);
		assertError(create(firstToken, json), 409, "DUPLICATE_BUSINESS_REGISTRATION_NUMBER");
		verifyNoInteractions(ntsClient);
		assertThat(workplaceCount()).isEqualTo(1);
	}

	@Test
	void databaseFailureRollsBackCreationAndReturnsSafeError() throws Exception {
		String token = token(createUser("EMPLOYER"));
		when(ntsClient.isActiveBusiness(anyString())).thenReturn(true);
		jdbc.execute("alter table workplace add constraint test_create_failure check (workplace_name <> '테스트 사업장')");
		try {
			assertError(create(token, mapper.writeValueAsString(validRequest())), 500, "INTERNAL_SERVER_ERROR");
			assertThat(workplaceCount()).isZero();
		} finally {
			jdbc.execute("alter table workplace drop constraint test_create_failure");
		}
	}

	private Map<String, Object> validRequest() {
		return new LinkedHashMap<>(Map.of("workplaceName", "테스트 사업장",
				"businessRegistrationNumber", "123-45-67890", "businessTypeId", 1, "employeeCountRange", "UNDER_5",
				"workplaceAddress", "테스트 주소", "latitude", 37.1234567, "longitude", 127.1234567));
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id", Long.class,
				UUID.randomUUID() + "@example.com", role);
	}

	private int workplaceCount() { return jdbc.queryForObject("select count(*) from workplace", Integer.class); }

	private void assertNoUploadFiles() throws Exception {
		try (var files = Files.list(uploadDirectory)) { assertThat(files.toList()).isEmpty(); }
	}

	private String token(long userId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", "EMPLOYER").claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpRequest.Builder request(String path, String token) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
		if (token != null) { builder.header("Authorization", "Bearer " + token); }
		return builder;
	}

	private HttpRequest createRequest(String token, String json) {
		return request("/api/workplaces", token).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build();
	}

	private HttpResponse<String> create(String token, String json) throws Exception {
		return client.send(createRequest(token, json), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest ocrRequest(String token, byte[] bytes) {
		return request("/api/workplaces/business-registration/ocr", token).header("Content-Type", "multipart/form-data; boundary=ocr-test")
				.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
	}

	private HttpResponse<String> ocr(String token, byte[] bytes, String type) throws Exception {
		return client.send(ocrRequest(token, multipart(bytes, type, 1)), HttpResponse.BodyHandlers.ofString());
	}

	private byte[] multipart(byte[] bytes, String type, int count) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		for (int i = 0; i < count; i++) {
			output.writeBytes(("--ocr-test\r\nContent-Disposition: form-data; name=\"businessRegistrationFile\"; filename=\"certificate\"\r\n"
					+ "Content-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			output.writeBytes(bytes);
			output.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
		}
		output.writeBytes("--ocr-test--\r\n".getBytes(StandardCharsets.UTF_8));
		return output.toByteArray();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message");
		assertThat(body.path("code").asString()).isEqualTo(code);
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
