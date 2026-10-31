package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentProfileReadIntegrationTest {

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
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, "
				+ "phone_verification_send_attempt, refresh_token, auth_account, user_account, study_program RESTART IDENTITY");
		jdbc.update("delete from visa_type where visa_code = 'TEST-VISA'");
	}

	@Test
	void returnsOwnBasicProfileWithNullOnboardingFieldsAndEmptyCareers() throws Exception {
		long userId = createUser("STUDENT");
		createProfile(userId);
		long otherId = createUser("STUDENT");
		createProfile(otherId);
		jdbc.update("update user_account set name = '다른 회원' where id = ?", otherId);
		Map<String, Object> before = jdbc.queryForMap("select * from user_account where id = ?", userId);
		HttpResponse<String> response = get(token(userId, "STUDENT"), "?userId=" + otherId);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(20);
		assertThat(body.path("studentId").asLong()).isEqualTo(userId);
		assertThat(body.path("name").asString()).isEqualTo("테스트 이름");
		assertThat(body.path("email").asString()).isEqualTo(before.get("email"));
		assertThat(body.path("phoneNumber").asString()).isEqualTo("010-0000-0001");
		assertThat(body.path("birthDate").asString()).isEqualTo("2000-03-14");
		assertThat(body.path("nationality").asString()).isEqualTo("VN");
		assertThat(body.path("currentRegion").asString()).isEqualTo("서울 마포구");
		assertThat(body.path("koreanLevel").asString()).isEqualTo("INTERMEDIATE");
		for (String field : List.of("visaCode", "universityName", "enrollmentStartDate", "studyProgramId", "gradeLevel",
				"academicState", "topikLevel", "stayStartDate", "stayExpirationDate", "profileImageUrl", "hirelinkBadgeIssuedAt")) {
			assertThat(body.has(field)).as(field).isTrue();
			assertThat(body.path(field).isNull()).as(field).isTrue();
		}
		assertThat(body.path("careers").isArray()).isTrue();
		assertThat(body.path("careers").size()).isZero();
		assertThat(jdbc.queryForMap("select * from user_account where id = ?", userId)).isEqualTo(before);
		verifyNoInteractions(storageClient);
	}

	@Test
	void returnsOnboardingDataAndOwnCareersInApprovedOrder() throws Exception {
		long userId = createUser("STUDENT");
		createProfile(userId);
		jdbc.update("insert into visa_type(visa_code, visa_name) values ('TEST-VISA', '테스트 비자')");
		Long programId = jdbc.queryForObject("insert into study_program(program_name) values ('테스트 학업과정') returning id", Long.class);
		jdbc.update("""
				update student_profile set visa_code = 'TEST-VISA', university_name = '테스트 학교',
				enrollment_start_date = '2024-03-01', study_program_id = ?, grade_level = 2, academic_state = 'ENROLLED',
				topik_level = 4, stay_start_date = '2025-03-01', stay_expiration_date = '2027-02-28',
				hirelink_badge_issued_at = '2026-10-20T01:00:00Z' where id = ?
				""", programId, userId);
		long older = createCareer(userId, "이전 경력", "2024-01", "2024-12", null);
		long sameMonthFirst = createCareer(userId, "최근 경력 1", "2025-03", "2025-06", "테스트 설명");
		long sameMonthSecond = createCareer(userId, "최근 경력 2", "2025-03", "2025-09", null);
		long otherId = createUser("STUDENT");
		createProfile(otherId);
		createCareer(otherId, "다른 회원 경력", "2026-01", "2026-02", null);

		HttpResponse<String> response = get(token(userId, "STUDENT"), "");
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("visaCode").asString()).isEqualTo("TEST-VISA");
		assertThat(body.path("universityName").asString()).isEqualTo("테스트 학교");
		assertThat(body.path("enrollmentStartDate").asString()).isEqualTo("2024-03-01");
		assertThat(body.path("studyProgramId").asLong()).isEqualTo(programId);
		assertThat(body.path("gradeLevel").asInt()).isEqualTo(2);
		assertThat(body.path("academicState").asString()).isEqualTo("ENROLLED");
		assertThat(body.path("topikLevel").asInt()).isEqualTo(4);
		assertThat(body.path("stayStartDate").asString()).isEqualTo("2025-03-01");
		assertThat(body.path("stayExpirationDate").asString()).isEqualTo("2027-02-28");
		assertThat(Instant.parse(body.path("hirelinkBadgeIssuedAt").asString())).isEqualTo(Instant.parse("2026-10-20T01:00:00Z"));
		JsonNode careers = body.path("careers");
		assertThat(careers.size()).isEqualTo(3);
		assertThat(careers.get(0).size()).isEqualTo(5);
		assertThat(careers.get(0).path("careerId").asLong()).isEqualTo(sameMonthSecond);
		assertThat(careers.get(1).path("careerId").asLong()).isEqualTo(sameMonthFirst);
		assertThat(careers.get(2).path("careerId").asLong()).isEqualTo(older);
		assertThat(careers.get(0).path("careerName").asString()).isEqualTo("최근 경력 2");
		assertThat(careers.get(0).path("startMonth").asString()).isEqualTo("2025-03");
		assertThat(careers.get(0).path("endMonth").asString()).isEqualTo("2025-09");
		assertThat(careers.get(0).path("description").isNull()).isTrue();
		assertThat(careers.get(1).path("description").asString()).isEqualTo("테스트 설명");
		verifyNoInteractions(storageClient);
	}

	@Test
	void requiresAuthenticationAndCurrentStudentRole() throws Exception {
		assertError(get(null, ""), 401, "UNAUTHORIZED");
		assertError(get("invalid-token", ""), 401, "UNAUTHORIZED");
		assertError(get(token(999L, "STUDENT"), ""), 401, "UNAUTHORIZED");
		long userId = createUser("EMPLOYER");
		assertError(get(token(userId, "EMPLOYER"), ""), 403, "FORBIDDEN");
		assertError(get(token(userId, "STUDENT"), ""), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'STUDENT', withdrawn_at = now() where id = ?", userId);
		assertError(get(token(userId, "STUDENT"), ""), 403, "WITHDRAWN_ACCOUNT");
		verifyNoInteractions(storageClient);
	}

	@Test
	void returnsNotFoundOnlyWhenOwnProfileDoesNotExist() throws Exception {
		long userId = createUser("STUDENT");
		long otherId = createUser("STUDENT");
		createProfile(otherId);
		assertError(get(token(userId, "STUDENT"), "?userId=" + otherId), 404, "STUDENT_PROFILE_NOT_FOUND");
		verifyNoInteractions(storageClient);
	}

	@Test
	void requestsImageUrlOnEachReadWithoutReturningOrReplacingStoredKey() throws Exception {
		long userId = createUser("STUDENT");
		createProfile(userId);
		String imageKey = "profiles/" + userId + "/photo.png";
		jdbc.update("update student_profile set profile_image_key = ? where id = ?", imageKey, userId);
		String firstUrl = "https://storage.example.com/image?token=first-test-token";
		String secondUrl = "https://storage.example.com/image?token=second-test-token";
		when(storageClient.createSignedUrl(imageKey)).thenReturn(firstUrl, secondUrl);

		HttpResponse<String> first = get(token(userId, "STUDENT"), "");
		HttpResponse<String> second = get(token(userId, "STUDENT"), "");
		assertThat(first.statusCode()).isEqualTo(200);
		assertThat(second.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(first.body()).path("profileImageUrl").asString()).isEqualTo(firstUrl);
		assertThat(mapper.readTree(second.body()).path("profileImageUrl").asString()).isEqualTo(secondUrl);
		assertThat(mapper.readTree(first.body()).has("profileImageKey")).isFalse();
		assertThat(jdbc.queryForObject("select profile_image_key from student_profile where id = ?", String.class, userId))
				.isEqualTo(imageKey);
		verify(storageClient, times(2)).createSignedUrl(imageKey);
	}

	@Test
	void imageSigningFailureReturnsServerErrorWithoutChangingProfile() throws Exception {
		long userId = createUser("STUDENT");
		createProfile(userId);
		String imageKey = "profiles/" + userId + "/photo.png";
		jdbc.update("update student_profile set profile_image_key = ? where id = ?", imageKey, userId);
		Map<String, Object> before = jdbc.queryForMap("select * from student_profile where id = ?", userId);
		when(storageClient.createSignedUrl(imageKey)).thenThrow(new ProfileImageAccessException());
		HttpResponse<String> response = get(token(userId, "STUDENT"), "");
		assertError(response, 500, "INTERNAL_SERVER_ERROR");
		assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
		assertThat(jdbc.queryForMap("select * from student_profile where id = ?", userId)).isEqualTo(before);
	}

	private long createUser(String role) {
		Long id = jdbc.queryForObject("insert into user_account(email, role, name) values (?, ?, '테스트 이름') returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
				String.format(java.util.Locale.ROOT, "010%08d", id), id);
		return id;
	}

	private void createProfile(long userId) {
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-03-14', 'VN', '서울 마포구', 'INTERMEDIATE')
				""", userId);
	}

	private long createCareer(long userId, String name, String startMonth, String endMonth, String description) {
		return jdbc.queryForObject("""
				insert into student_career(user_id, career_name, start_month, end_month, description)
				values (?, ?, ?, ?, ?) returning id
				""", Long.class, userId, name, startMonth, endMonth, description);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> get(String token, String query) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/students/me/profile" + query))
				.timeout(Duration.ofSeconds(15)).GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
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
