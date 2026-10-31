package com.hirelink.domain.employmentmatch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmploymentMatchDetailIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final Instant MATCHED_AT = Instant.parse("2026-11-01T03:00:00.123456Z");

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
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void studentReadsSnapshotConditionsAndCurrentEmployerContactWithoutStudentDetailOrStateChanges() throws Exception {
		Fixture fixture = createCompletedMatch();
		Map<String, Object> applicationBefore = row("application", fixture.applicationId());
		Map<String, Object> offerBefore = row("employment_offer", fixture.offerId());
		Map<String, Object> matchBefore = row("employment_match", fixture.matchId());

		HttpResponse<String> response = get(token(fixture.studentId(), "STUDENT"), fixture.matchId());

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("matchId", "applicationId", "offerId",
				"jobPostingId", "jobTitle", "workplaceName", "workplaceAddress", "wageType", "wageAmount",
				"workTimes", "desiredStartDate", "matchedAt", "contact", "student");
		assertThat(body.path("matchId").asLong()).isEqualTo(fixture.matchId());
		assertThat(body.path("applicationId").asLong()).isEqualTo(fixture.applicationId());
		assertThat(body.path("offerId").asLong()).isEqualTo(fixture.offerId());
		assertThat(body.path("jobPostingId").asLong()).isEqualTo(fixture.jobPostingId());
		assertThat(body.path("jobTitle").asString()).isEqualTo("지원 당시 공고");
		assertThat(body.path("workplaceName").asString()).isEqualTo("지원 당시 사업장");
		assertThat(body.path("workplaceAddress").asString()).isEqualTo("서울 마포구 지원로 1");
		assertThat(body.path("wageType").asString()).isEqualTo("HOURLY");
		assertThat(body.path("wageAmount").asInt()).isEqualTo(12500);
		assertThat(body.path("workTimes").size()).isEqualTo(1);
		assertThat(body.path("workTimes").get(0).path("dayOfWeek").asString()).isEqualTo("MONDAY");
		assertThat(body.path("workTimes").get(0).path("startTime").asString()).isEqualTo("09:00:00");
		assertThat(body.path("workTimes").get(0).path("endTime").asString()).isEqualTo("18:00:00");
		assertThat(body.path("desiredStartDate").asString()).isEqualTo("2026-11-10");
		assertThat(Instant.parse(body.path("matchedAt").asString())).isEqualTo(MATCHED_AT);
		assertThat(body.path("contact").path("name").asString()).isEqualTo("현재 사업주");
		assertThat(body.path("contact").path("phoneNumber").asString()).isEqualTo("010-2222-2222");
		assertThat(body.path("student").isNull()).isTrue();
		assertThat(row("application", fixture.applicationId())).isEqualTo(applicationBefore);
		assertThat(row("employment_offer", fixture.offerId())).isEqualTo(offerBefore);
		assertThat(row("employment_match", fixture.matchId())).isEqualTo(matchBefore);
		verifyNoInteractions(storageClient);
	}

	@Test
	void employerReadsSnapshotStudentDetailWithCurrentStudentContactAndProfileImage() throws Exception {
		Fixture fixture = createCompletedMatch();
		String imageUrl = "https://storage.example.com/current-profile?token=test";
		when(storageClient.createSignedUrl("profiles/current-student.png")).thenReturn(imageUrl);

		HttpResponse<String> response = get(token(fixture.employerId(), "EMPLOYER"), fixture.matchId());

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("contact").path("name").asString()).isEqualTo("현재 유학생");
		assertThat(body.path("contact").path("phoneNumber").asString()).isEqualTo("010-1111-1111");
		JsonNode student = body.path("student");
		assertThat(student.propertyNames()).containsExactlyInAnyOrder("name", "profileImageUrl", "nationality",
				"visaCode", "topikLevel");
		assertThat(student.path("name").asString()).isEqualTo("지원 당시 유학생");
		assertThat(student.path("profileImageUrl").asString()).isEqualTo(imageUrl);
		assertThat(student.path("nationality").asString()).isEqualTo("VN");
		assertThat(student.path("visaCode").asString()).isEqualTo("D-2-2");
		assertThat(student.path("topikLevel").asInt()).isEqualTo(4);
	}

	@Test
	void blocksUnrelatedStudentsAndEmployersAndReturnsNotFoundForMissingMatch() throws Exception {
		Fixture fixture = createCompletedMatch();
		long otherStudent = createStudent("제삼자 유학생", "01033333333");
		long otherEmployer = createUser("EMPLOYER", "다른 사업주", "01044444444");

		assertError(get(token(otherStudent, "STUDENT"), fixture.matchId()), 403, "FORBIDDEN");
		assertError(get(token(otherEmployer, "EMPLOYER"), fixture.matchId()), 403, "FORBIDDEN");
		assertError(get(token(fixture.studentId(), "STUDENT"), 99999), 404, "EMPLOYMENT_MATCH_NOT_FOUND");
		verifyNoInteractions(storageClient);
	}

	@Test
	void validatesAuthenticationAndPositiveMatchId() throws Exception {
		Fixture fixture = createCompletedMatch();

		assertError(get(null, fixture.matchId()), 401, "UNAUTHORIZED");
		assertError(get(token(fixture.studentId(), "STUDENT"), 0), 400, "INVALID_INPUT");
		verifyNoInteractions(storageClient);
	}

	@Test
	void returnsServerErrorWhenCurrentProfileImageCannotBeSignedWithoutChangingState() throws Exception {
		Fixture fixture = createCompletedMatch();
		Map<String, Object> applicationBefore = row("application", fixture.applicationId());
		Map<String, Object> offerBefore = row("employment_offer", fixture.offerId());
		Map<String, Object> matchBefore = row("employment_match", fixture.matchId());
		when(storageClient.createSignedUrl("profiles/current-student.png"))
				.thenThrow(new ProfileImageAccessException());

		assertError(get(token(fixture.employerId(), "EMPLOYER"), fixture.matchId()),
				500, "INTERNAL_SERVER_ERROR");
		assertThat(row("application", fixture.applicationId())).isEqualTo(applicationBefore);
		assertThat(row("employment_offer", fixture.offerId())).isEqualTo(offerBefore);
		assertThat(row("employment_match", fixture.matchId())).isEqualTo(matchBefore);
	}

	private Fixture createCompletedMatch() throws Exception {
		long student = createStudent("현재 유학생", "01011111111");
		jdbc.update("""
				update student_profile set nationality = 'US', visa_code = 'D-4-1', topik_level = 6,
				profile_image_key = 'profiles/current-student.png' where id = ?
				""", student);
		long employer = createUser("EMPLOYER", "현재 사업주", "01022222222");
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '현재 사업장', '현재 대표자', 'test/certificate.pdf', ?, 1,
				'서울 강남구 변경로 2', 37.1234567, 127.1234567, 'UNDER_5') returning id
				""", Long.class, employer, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		long posting = jdbc.queryForObject("""
				insert into job_posting(workplace_id, title, wage_type, wage_amount, status)
				values (?, '현재 공고', 'MONTHLY', 3000000, 'CLOSED') returning id
				""", Long.class, workplace);
		jdbc.update("""
				insert into job_posting_work_time(job_posting_id, day_of_week, start_time, end_time)
				values (?, 'TUESDAY', '10:00', '19:00')
				""", posting);
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 90.00, '{}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
		String snapshot = mapper.writeValueAsString(Map.of(
				"version", 1,
				"student", Map.of("studentProfileId", student, "name", "지원 당시 유학생",
						"nationality", "VN", "visaCode", "D-2-2", "topikLevel", 4,
						"profileImageKey", "profiles/snapshot-student.png"),
				"jobPosting", Map.of("jobPostingId", posting, "title", "지원 당시 공고",
						"workplaceName", "지원 당시 사업장", "workplaceAddress", "서울 마포구 지원로 1",
						"wageType", "HOURLY", "wageAmount", 12500,
						"workTimes", List.of(Map.of("dayOfWeek", "MONDAY", "startTime", "09:00",
								"endTime", "18:00"))),
				"recommendation", Map.of("recommendationId", recommendation)));
		long application = jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, employer_response_deadline, applied_at)
				values (?, ?, ?, '2026-11-10', 1, cast(? as jsonb), 'COMPLETED', now(), now()) returning id
				""", Long.class, recommendation, student, posting, snapshot);
		long offer = jdbc.queryForObject("""
				insert into employment_offer(application_id, status, student_response_deadline, offered_at, responded_at)
				values (?, 'ACCEPTED', now(), now(), ?) returning id
				""", Long.class, application, OffsetDateTime.ofInstant(MATCHED_AT, ZoneOffset.UTC));
		long match = jdbc.queryForObject("""
				insert into employment_match(employment_offer_id, completed_at) values (?, ?) returning id
				""", Long.class, offer, OffsetDateTime.ofInstant(MATCHED_AT, ZoneOffset.UTC));
		return new Fixture(student, employer, posting, application, offer, match);
	}

	private long createStudent(String name, String phoneNumber) {
		long student = createUser("STUDENT", name, phoneNumber);
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-01-01', 'KR', '서울', 'INTERMEDIATE')
				""", student);
		return student;
	}

	private long createUser(String role, String name, String phoneNumber) {
		long id = jdbc.queryForObject("""
				insert into user_account(email, role, name) values (?, ?, ?) returning id
				""", Long.class, UUID.randomUUID() + "@example.com", role, name);
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
				phoneNumber, id);
		return id;
	}

	private Map<String, Object> row(String table, long id) {
		return jdbc.queryForMap("select * from " + table + " where id = ?", id);
	}

	private HttpResponse<String> get(String token, long matchId) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + "/api/matches/" + matchId))
				.timeout(Duration.ofSeconds(15)).GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private record Fixture(long studentId, long employerId, long jobPostingId, long applicationId,
			long offerId, long matchId) {
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
