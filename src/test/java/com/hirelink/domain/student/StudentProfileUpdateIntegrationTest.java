package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import com.hirelink.global.exception.ProfileImageAccessException;
import com.hirelink.global.storage.SupabaseStorageClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentProfileUpdateIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
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
	private SupabaseStorageClient storageClient;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE student_career, student_profile, user_term_agreement, phone_verification, "
				+ "phone_verification_send_attempt, refresh_token, auth_account, user_account, study_program RESTART IDENTITY");
	}

	@Test
	void migrationRegistersExactlyTheNineApprovedVisaTypes() {
		Map<String, String> visas = jdbc.queryForList("select visa_code, visa_name from visa_type").stream()
				.collect(Collectors.toMap(row -> (String) row.get("visa_code"), row -> (String) row.get("visa_name")));
		assertThat(visas).isEqualTo(Map.of("D-2-1", "전문학사", "D-2-2", "학사", "D-2-3", "석사", "D-2-4", "박사",
				"D-2-6", "교환학생", "D-2-7", "일·학습연계", "D-2-8", "방문학생", "D-4-1", "한국어연수", "D-4-7", "외국어연수"));
		assertThat(jdbc.queryForObject("select count(*) from study_program", Integer.class)).isZero();
	}

	@Test
	void updatesOnlySentFieldsAndDoesNotDependOnStorage() throws Exception {
		long id = createStudent();
		long otherId = createStudent();
		Long programId = jdbc.queryForObject("insert into study_program(program_name) values ('테스트 학업과정') returning id", Long.class);
		jdbc.update("""
				update student_profile set study_program_id = ?, grade_level = 2, visa_code = 'D-2-2',
				academic_state = 'ENROLLED', topik_level = 4, profile_image_key = 'profiles/test.png',
				enrollment_start_date = '2024-03-01', stay_start_date = '2024-03-01',
				certification_prompt_dismissed_at = now(), hirelink_badge_issued_at = now() where id = ?
				""", programId, id);
		Map<String, Object> before = profile(id);
		Map<String, Object> otherBefore = profile(otherId);
		when(storageClient.createSignedUrl("profiles/test.png")).thenThrow(new ProfileImageAccessException());
		HttpResponse<String> response = patch(token(id, "STUDENT"), "{\"name\":\"수정한 이름\",\"currentRegion\":\"서울 용산구\"}");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(16);
		assertThat(body.path("name").asString()).isEqualTo("수정한 이름");
		assertThat(body.path("studentId").asLong()).isEqualTo(id);
		assertThat(body.has("profileImageUrl")).isFalse();
		assertThat(body.has("profileImageKey")).isFalse();
		assertThat(body.path("studyProgramId").asLong()).isEqualTo(programId);
		assertThat(body.path("gradeLevel").asInt()).isEqualTo(2);
		before.put("current_region", "서울 용산구");
		assertThat(profile(id)).isEqualTo(before);
		assertThat(profile(otherId)).isEqualTo(otherBefore);
		verifyNoInteractions(storageClient);

		doReturn("https://storage.example.com/test-photo").when(storageClient).createSignedUrl("profiles/test.png");
		HttpResponse<String> read = client.send(HttpRequest.newBuilder(URI.create(baseUrl()))
				.header("Authorization", "Bearer " + token(id, "STUDENT")).GET().build(), HttpResponse.BodyHandlers.ofString());
		assertThat(read.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(read.body()).path("studyProgramId").asLong()).isEqualTo(programId);
		assertThat(mapper.readTree(read.body()).path("gradeLevel").asInt()).isEqualTo(2);
	}

	@Test
	void acceptsAllEditableFieldsAndKeepsPastExpirationDate() throws Exception {
		long id = createStudent();
		String request = """
				{"name":"새 이름","birthDate":"2000-05-10","nationality":"US","currentRegion":"서울 용산구",
				"koreanLevel":"ADVANCED","visaCode":"D-4-1","stayExpirationDate":"2020-03-01",
				"universityName":"테스트 학교","academicState":"IN_PROGRESS","topikLevel":6}
				""";
		HttpResponse<String> response = patch(token(id, "STUDENT"), request);
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = mapper.readTree(response.body());
		JsonNode sent = mapper.readTree(request);
		for (String field : List.of("name", "birthDate", "nationality", "currentRegion", "koreanLevel", "visaCode",
				"stayExpirationDate", "universityName", "academicState", "topikLevel")) {
			assertThat(body.path(field)).as(field).isEqualTo(sent.path(field));
		}
		assertThat(profile(id)).containsEntry("study_program_id", null).containsEntry("grade_level", null);
		assertThat(jdbc.queryForObject("select name from user_account where id = ?", String.class, id)).isEqualTo("새 이름");
	}

	@Test
	void distinguishesMissingTopikFromExplicitNullAndAcceptsLowestLevel() throws Exception {
		long id = createStudent();
		jdbc.update("update student_profile set topik_level = 4 where id = ?", id);
		String token = token(id, "STUDENT");
		assertThat(patch(token, "{\"name\":\"이름 수정\"}").statusCode()).isEqualTo(200);
		assertThat(profile(id).get("topik_level")).isEqualTo(4);
		assertThat(patch(token, "{\"topikLevel\":null}").statusCode()).isEqualTo(200);
		assertThat(profile(id).get("topik_level")).isNull();
		assertThat(patch(token, "{\"topikLevel\":1}").statusCode()).isEqualTo(200);
		assertThat(profile(id).get("topik_level")).isEqualTo(1);
	}

	@Test
	void rejectsIncompatibleVisaChangeWithoutErasingStateOrSavingName() throws Exception {
		long id = createStudent();
		jdbc.update("update student_profile set visa_code = 'D-2-2', academic_state = 'ENROLLED' where id = ?", id);
		Map<String, Object> before = profile(id);
		Map<String, Object> userBefore = jdbc.queryForMap("select * from user_account where id = ?", id);
		String token = token(id, "STUDENT");
		assertError(patch(token, "{\"name\":\"저장되면 안 됨\",\"visaCode\":\"D-4-1\"}"), 400, "INVALID_INPUT");
		assertThat(profile(id)).isEqualTo(before);
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", id)).isEqualTo(userBefore);
		assertThat(patch(token, "{\"visaCode\":\"D-4-1\",\"academicState\":\"IN_PROGRESS\"}").statusCode()).isEqualTo(200);
		assertThat(profile(id)).containsEntry("visa_code", "D-4-1").containsEntry("academic_state", "IN_PROGRESS");
	}

	@Test
	void preservesCompatibleStateAndPermitsVisaOnlyBeforeAcademicStateIsEntered() throws Exception {
		long id = createStudent();
		String token = token(id, "STUDENT");
		assertThat(patch(token, "{\"visaCode\":\"D-2-2\"}").statusCode()).isEqualTo(200);
		assertThat(profile(id).get("academic_state")).isNull();
		assertThat(patch(token, "{\"academicState\":\"ENROLLED\"}").statusCode()).isEqualTo(200);
		assertThat(patch(token, "{\"visaCode\":\"D-2-3\"}").statusCode()).isEqualTo(200);
		assertThat(profile(id).get("academic_state")).isEqualTo("ENROLLED");
		long newId = createStudent();
		assertError(patch(token(newId, "STUDENT"), "{\"academicState\":\"ENROLLED\"}"), 400, "INVALID_INPUT");
	}

	@Test
	void enforcesAcademicChoicesForEveryVisaGroup() throws Exception {
		Map<String, List<String>> choices = Map.of(
				"D-2-1", List.of("ENROLLED", "ON_LEAVE", "COMPLETED", "GRADUATED"),
				"D-2-2", List.of("ENROLLED", "ON_LEAVE", "COMPLETED", "GRADUATED"),
				"D-2-3", List.of("ENROLLED", "ON_LEAVE", "COMPLETED", "GRADUATED"),
				"D-2-4", List.of("ENROLLED", "ON_LEAVE", "COMPLETED", "GRADUATED"),
				"D-2-6", List.of("IN_PROGRESS", "COMPLETED", "DISCONTINUED"),
				"D-2-7", List.of("IN_PROGRESS", "ON_LEAVE_OR_DISCONTINUED", "COMPLETED"),
				"D-2-8", List.of("IN_PROGRESS", "COMPLETED", "DISCONTINUED"),
				"D-4-1", List.of("IN_PROGRESS", "COMPLETED", "DISCONTINUED"),
				"D-4-7", List.of("IN_PROGRESS", "COMPLETED", "DISCONTINUED"));
		long id = createStudent();
		String token = token(id, "STUDENT");
		for (var entry : choices.entrySet()) {
			for (String state : entry.getValue()) {
				assertThat(patch(token, mapper.writeValueAsString(Map.of("visaCode", entry.getKey(), "academicState", state)))
						.statusCode()).as(entry.getKey() + ": " + state).isEqualTo(200);
			}
		}
		assertError(patch(token, "{\"visaCode\":\"D-2-7\",\"academicState\":\"ON_LEAVE\"}"), 400, "INVALID_INPUT");
		assertError(patch(token, "{\"visaCode\":\"D-4-1\",\"academicState\":\"GRADUATED\"}"), 400, "INVALID_INPUT");
	}

	@Test
	void rejectsEmptyUnknownReadOnlyAndExplicitNullFieldsWithoutChangingData() throws Exception {
		long id = createStudent();
		String token = token(id, "STUDENT");
		Map<String, Object> before = profile(id);
		for (String json : List.of("{}", "null", "[]", "{", "{\"studyProgramId\":1}", "{\"gradeLevel\":2}",
				"{\"profileImageUrl\":\"https://example.com/photo\"}", "{\"userId\":999}", "{\"email\":\"test@example.com\"}",
				"{\"phoneNumber\":\"010-0000-0000\"}", "{\"topikLevelProvided\":true}", "{\"unknown\":1}")) {
			assertError(patch(token, json), 400, "INVALID_INPUT");
		}
		for (String field : List.of("name", "birthDate", "nationality", "currentRegion", "koreanLevel", "visaCode",
				"stayExpirationDate", "universityName", "academicState")) {
			assertError(patch(token, "{\"" + field + "\":null}"), 400, "INVALID_INPUT");
		}
		assertThat(profile(id)).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "7", "1.5", "\"5\"", "true", "2147483648"})
	void rejectsInvalidTopikValues(String value) throws Exception {
		long id = createStudent();
		assertError(patch(token(id, "STUDENT"), "{\"topikLevel\":" + value + "}"), 400, "INVALID_INPUT");
		assertThat(profile(id).get("topik_level")).isNull();
	}

	@Test
	void validatesTextAndDateInputsWithoutSavingOtherChanges() throws Exception {
		long id = createStudent();
		String token = token(id, "STUDENT");
		for (String json : List.of("{\"name\":\" \"}", "{\"currentRegion\":\"\"}", "{\"universityName\":\" \"}",
				"{\"nationality\":\"ZZ\"}", "{\"nationality\":\"vn\"}", "{\"koreanLevel\":\"EXPERT\"}",
				"{\"visaCode\":\"D-2-5\"}", "{\"visaCode\":\"D-2-2\",\"academicState\":\"INVALID\"}",
				"{\"birthDate\":\"2020-02-30\"}", "{\"stayExpirationDate\":\"2025/03/01\"}",
				"{\"name\":123}", "{\"birthDate\":[2000,3,14]}", "{\"name\":\"" + "가".repeat(101) + "\"}",
				"{\"universityName\":\"" + "가".repeat(151) + "\"}")) {
			assertError(patch(token, json), 400, "INVALID_INPUT");
		}
		assertThat(jdbc.queryForObject("select name from user_account where id = ?", String.class, id)).isEqualTo("테스트 이름");
	}

	@Test
	void validatesAgeBoundaryAndExpirationAgainstExistingStartDate() throws Exception {
		long id = createStudent();
		String token = token(id, "STUDENT");
		LocalDate threshold = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(18);
		assertError(patch(token, mapper.writeValueAsString(Map.of("birthDate", threshold.plusDays(1)))), 400, "INVALID_INPUT");
		assertThat(patch(token, mapper.writeValueAsString(Map.of("birthDate", threshold))).statusCode()).isEqualTo(200);
		jdbc.update("update student_profile set stay_start_date = '2020-03-01' where id = ?", id);
		assertError(patch(token, "{\"stayExpirationDate\":\"2020-02-29\"}"), 400, "INVALID_INPUT");
		assertThat(patch(token, "{\"stayExpirationDate\":\"2020-03-01\"}").statusCode()).isEqualTo(200);
	}

	@Test
	void requiresCurrentStudentAccountAndOwnExistingProfile() throws Exception {
		assertError(patch(null, "{\"name\":\"이름\"}"), 401, "UNAUTHORIZED");
		assertError(patch("invalid-token", "{\"name\":\"이름\"}"), 401, "UNAUTHORIZED");
		assertError(patch(token(999, "STUDENT"), "{\"name\":\"이름\"}"), 401, "UNAUTHORIZED");
		long id = createStudent();
		jdbc.update("update user_account set role = 'EMPLOYER' where id = ?", id);
		assertError(patch(token(id, "STUDENT"), "{\"name\":\"이름\"}"), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'STUDENT', withdrawn_at = now() where id = ?", id);
		assertError(patch(token(id, "STUDENT"), "{\"name\":\"이름\"}"), 403, "WITHDRAWN_ACCOUNT");
		jdbc.update("update user_account set withdrawn_at = null where id = ?", id);
		jdbc.update("delete from student_profile where id = ?", id);
		createStudent();
		assertError(patch(token(id, "STUDENT"), "{\"name\":\"이름\"}"), 404, "STUDENT_PROFILE_NOT_FOUND");
	}

	@Test
	void rollsBackNameWhenDatabaseRejectsProfileUpdate() throws Exception {
		long id = createStudent();
		Map<String, Object> before = profile(id);
		Map<String, Object> userBefore = jdbc.queryForMap("select * from user_account where id = ?", id);
		jdbc.execute("alter table student_profile add constraint test_reject_region check (current_region <> '실패 지역')");
		try {
			assertError(patch(token(id, "STUDENT"), "{\"name\":\"저장되면 안 됨\",\"currentRegion\":\"실패 지역\"}"),
					500, "INTERNAL_SERVER_ERROR");
			assertThat(profile(id)).isEqualTo(before);
			assertThat(jdbc.queryForMap("select * from user_account where id = ?", id)).isEqualTo(userBefore);
		} finally {
			jdbc.execute("alter table student_profile drop constraint test_reject_region");
		}
	}

	@Test
	void simultaneousPartialUpdatesPreserveBothChanges() throws Exception {
		long id = createStudent();
		String token = token(id, "STUDENT");
		CompletableFuture<HttpResponse<String>> region = client.sendAsync(request(token, "{\"currentRegion\":\"서울 용산구\"}"),
				HttpResponse.BodyHandlers.ofString());
		CompletableFuture<HttpResponse<String>> topik = client.sendAsync(request(token, "{\"topikLevel\":5}"),
				HttpResponse.BodyHandlers.ofString());
		assertThat(region.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(topik.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(profile(id)).containsEntry("current_region", "서울 용산구").containsEntry("topik_level", 5);
	}

	private long createStudent() {
		long id = jdbc.queryForObject("insert into user_account(email, role, name) values (?, 'STUDENT', '테스트 이름') returning id",
				Long.class, UUID.randomUUID() + "@example.com");
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
				String.format(java.util.Locale.ROOT, "010%08d", id), id);
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-03-14', 'VN', '서울 마포구', 'INTERMEDIATE')
				""", id);
		return id;
	}

	private Map<String, Object> profile(long id) {
		return jdbc.queryForMap("select * from student_profile where id = ?", id);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpRequest request(String token, String json) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl())).timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers.ofString(json));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return request.build();
	}

	private HttpResponse<String> patch(String token, String json) throws Exception {
		return client.send(request(token, json), HttpResponse.BodyHandlers.ofString());
	}

	private String baseUrl() {
		return "http://localhost:" + port + "/api/students/me/profile";
	}

	private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
