package com.hirelink.domain.application;

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

import com.hirelink.global.storage.SupabaseStorageClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationQueryIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final Instant BASE_TIME = Instant.parse("2026-11-01T01:00:00Z");

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
	void employerReadsApplicationDetailFromSnapshotWithoutContact() throws Exception {
		long employer = createUser("EMPLOYER", "현재 사업주");
		long student = createStudent("현재 유학생");
		long posting = createPosting(employer, "현재 공고");
		ApplicationFixture fixture = createApplication(student, posting, "WAITING_RESPONSE", BASE_TIME, "지원 당시 유학생");
		when(storageClient.createSignedUrl("profiles/snapshot.png"))
				.thenReturn("https://storage.example.com/snapshot-profile");

		HttpResponse<String> response = get(token(employer, "EMPLOYER"),
				"/api/employer/applications/" + fixture.applicationId());

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("status").asString()).isEqualTo("WAITING_RESPONSE");
		assertThat(body.path("applicant").path("name").asString()).isEqualTo("지원 당시 유학생");
		assertThat(body.path("applicant").path("profileImageUrl").asString())
				.isEqualTo("https://storage.example.com/snapshot-profile");
		assertThat(body.path("applicant").path("visaType").asString()).isEqualTo("D-2-2");
		assertThat(body.path("applicant").path("careers").get(0).path("careerName").asString())
				.isEqualTo("지원 당시 카페");
		assertThat(body.path("jobRequirements").path("wageAmount").asInt()).isEqualTo(13000);
		assertThat(body.path("matchResult").path("matchScore").decimalValue()).isEqualByComparingTo("85.00");
		assertThat(body.path("matchResult").path("workTimeMatched").asBoolean()).isTrue();
		assertThat(body.path("matchResult").path("experienceMatched").asBoolean()).isFalse();
		assertThat(response.body()).doesNotContain("phone", "contact");
	}

	@Test
	void employerListExcludesEndedAndCountsOnlyWaitingAndOfferedAsOccupied() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "공고");
		ApplicationFixture waiting = createApplication(createStudent("대기"), posting,
				"WAITING_RESPONSE", BASE_TIME, "대기 스냅샷");
		ApplicationFixture offered = createApplication(createStudent("제안"), posting,
				"OFFERED", BASE_TIME.plusSeconds(60), "제안 스냅샷");
		ApplicationFixture completed = createApplication(createStudent("완료"), posting,
				"COMPLETED", BASE_TIME.plusSeconds(120), "완료 스냅샷");
		createApplication(createStudent("종료"), posting, "ENDED", BASE_TIME.plusSeconds(180), "종료 스냅샷");

		JsonNode body = body(get(token(employer, "EMPLOYER"),
				"/api/job-postings/" + posting + "/applications"), 200);

		assertThat(body.path("capacity").asInt()).isEqualTo(5);
		assertThat(body.path("occupiedCount").asInt()).isEqualTo(2);
		assertThat(body.path("waitingResponseCount").asInt()).isEqualTo(1);
		assertThat(body.path("offeredCount").asInt()).isEqualTo(1);
		assertThat(body.path("completedCount").asInt()).isEqualTo(1);
		JsonNode applications = body.path("applications");
		assertThat(applications.size()).isEqualTo(3);
		assertThat(applications.get(0).path("applicationId").asLong()).isEqualTo(completed.applicationId());
		assertThat(applications.get(1).path("applicationId").asLong()).isEqualTo(offered.applicationId());
		assertThat(applications.get(2).path("applicationId").asLong()).isEqualTo(waiting.applicationId());
		assertThat(applications.get(0).path("matchId").asLong()).isEqualTo(completed.matchId());
		assertThat(applications.get(1).path("offerId").asLong()).isEqualTo(offered.offerId());
		assertThat(body.toString()).doesNotContain("phone", "contact");

		JsonNode filtered = body(get(token(employer, "EMPLOYER"),
				"/api/job-postings/" + posting + "/applications?status=WAITING_RESPONSE"), 200);
		assertThat(filtered.path("applications").size()).isEqualTo(1);
		assertThat(filtered.path("occupiedCount").asInt()).isEqualTo(2);
	}

	@Test
	void applicationListsValidateRoleOwnershipAndStatusFilter() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long otherEmployer = createUser("EMPLOYER", "다른 사업주");
		long student = createStudent("유학생");
		long posting = createPosting(employer, "공고");
		ApplicationFixture fixture = createApplication(student, posting, "WAITING_RESPONSE", BASE_TIME, "스냅샷");

		assertError(get(token(otherEmployer, "EMPLOYER"),
				"/api/job-postings/" + posting + "/applications"), 403, "FORBIDDEN");
		assertError(get(token(student, "STUDENT"),
				"/api/job-postings/" + posting + "/applications"), 403, "FORBIDDEN");
		assertError(get(token(employer, "EMPLOYER"),
				"/api/job-postings/99999/applications"), 404, "JOB_POSTING_NOT_FOUND");
		assertError(get(token(employer, "EMPLOYER"),
				"/api/job-postings/" + posting + "/applications?status=ENDED"), 400, "INVALID_INPUT");
		assertError(get(token(createStudent("다른 유학생"), "STUDENT"),
				"/api/applications/" + fixture.applicationId()), 403, "FORBIDDEN");
		assertError(get(null, "/api/applications/me"), 401, "UNAUTHORIZED");
	}

	@Test
	void studentReadsDetailWithOfferAndNoContact() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long student = createStudent("유학생");
		long posting = createPosting(employer, "현재 제목");
		ApplicationFixture fixture = createApplication(student, posting, "OFFERED", BASE_TIME, "지원 당시 이름");

		JsonNode body = body(get(token(student, "STUDENT"),
				"/api/applications/" + fixture.applicationId()), 200);

		assertThat(body.path("status").asString()).isEqualTo("OFFERED");
		assertThat(body.path("jobTitle").asString()).isEqualTo("지원 당시 공고");
		assertThat(body.path("workplaceName").asString()).isEqualTo("지원 당시 사업장");
		assertThat(body.path("workTimes").get(0).path("startTime").asString()).isEqualTo("10:00");
		assertThat(body.path("employerResponseDeadline").isNull()).isTrue();
		assertThat(body.path("offerId").asLong()).isEqualTo(fixture.offerId());
		assertThat(body.path("offerStatus").asString()).isEqualTo("PENDING");
		assertThat(body.path("matchId").isNull()).isTrue();
		assertThat(body.toString()).doesNotContain("phone", "contact");
	}

	@Test
	void studentListExcludesEndedSortsAndReturnsSavedRecommendationReasons() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long student = createStudent("유학생");
		ApplicationFixture waiting = createApplication(student, createPosting(employer, "대기 공고"),
				"WAITING_RESPONSE", BASE_TIME, "대기 이름");
		ApplicationFixture offered = createApplication(student, createPosting(employer, "제안 공고"),
				"OFFERED", BASE_TIME.plusSeconds(60), "제안 이름");
		ApplicationFixture completed = createApplication(student, createPosting(employer, "완료 공고"),
				"COMPLETED", BASE_TIME.plusSeconds(120), "완료 이름");
		createApplication(student, createPosting(employer, "종료 공고"),
				"ENDED", BASE_TIME.plusSeconds(180), "종료 이름");

		JsonNode body = body(get(token(student, "STUDENT"), "/api/applications/me"), 200);
		JsonNode applications = body.path("applications");
		assertThat(applications.size()).isEqualTo(3);
		assertThat(applications.get(0).path("applicationId").asLong()).isEqualTo(completed.applicationId());
		assertThat(applications.get(1).path("applicationId").asLong()).isEqualTo(offered.applicationId());
		assertThat(applications.get(2).path("applicationId").asLong()).isEqualTo(waiting.applicationId());
		assertThat(applications.get(0).path("recommendationReasons").get(0).asString())
				.isEqualTo("저장된 추천 이유");
		assertThat(applications.get(0).path("matchId").asLong()).isEqualTo(completed.matchId());
		assertThat(body.toString()).doesNotContain("phone", "contact");

		JsonNode filtered = body(get(token(student, "STUDENT"),
				"/api/applications/me?status=COMPLETED"), 200);
		assertThat(filtered.path("applications").size()).isEqualTo(1);
		assertError(get(token(student, "STUDENT"),
				"/api/applications/me?status=ENDED"), 400, "INVALID_INPUT");
	}

	private ApplicationFixture createApplication(long student, long posting, String status, Instant appliedAt,
			String snapshotName) throws Exception {
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 85.00, '{"summary":"저장된 추천 이유"}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
		String snapshot = """
				{
				  "version":1,
				  "student":{"studentProfileId":%d,"name":"%s","profileImageKey":"profiles/snapshot.png",
				    "nationality":"VN","visaCode":"D-2-2","stayExpirationDate":"2027-03-14",
				    "koreanLevel":"INTERMEDIATE","topikLevel":4,
				    "careers":[{"careerName":"지원 당시 카페","startMonth":"2026-01","endMonth":"2026-08","description":"응대"}],
				    "availableWorkTimes":[{"dayOfWeek":"MONDAY","startTime":"15:00","endTime":"21:00"}]},
				  "jobPosting":{"jobPostingId":%d,"title":"지원 당시 공고","workplaceName":"지원 당시 사업장",
				    "workplaceAddress":"서울 지원로 1","wageType":"HOURLY","wageAmount":13000,
				    "requiredKoreanLevel":"INTERMEDIATE",
				    "workTimes":[{"dayOfWeek":"SAT","startTime":"10:00","endTime":"16:00"}]},
				  "recommendation":{"recommendationId":%d,"score":85.00,
				    "reason":{"recommendationReasons":["저장된 추천 이유"],"workTimeMatched":true,
				      "experienceMatched":false,"locationMatched":true,"wageMatched":true,"koreanLevelMatched":true}}
				}
				""".formatted(student, snapshotName, posting, recommendation);
		String endReason = status.equals("ENDED") ? "WITHDRAWN" : null;
		long application = jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, end_reason, employer_response_deadline, applied_at, ended_at)
				values (?, ?, ?, '2026-11-10', 1, cast(? as jsonb), ?, ?, ?, ?, ?) returning id
				""", Long.class, recommendation, student, posting, snapshot, status, endReason,
				OffsetDateTime.ofInstant(appliedAt.plusSeconds(86400), ZoneOffset.UTC),
				OffsetDateTime.ofInstant(appliedAt, ZoneOffset.UTC),
				status.equals("ENDED") ? OffsetDateTime.ofInstant(appliedAt.plusSeconds(300), ZoneOffset.UTC) : null);
		Long offer = null;
		Long match = null;
		if (status.equals("OFFERED") || status.equals("COMPLETED")) {
			String offerStatus = status.equals("COMPLETED") ? "ACCEPTED" : "PENDING";
			offer = jdbc.queryForObject("""
					insert into employment_offer(application_id, status, student_response_deadline, offered_at, responded_at)
					values (?, ?, ?, ?, ?) returning id
					""", Long.class, application, offerStatus,
					OffsetDateTime.ofInstant(appliedAt.plusSeconds(90000), ZoneOffset.UTC),
					OffsetDateTime.ofInstant(appliedAt.plusSeconds(3600), ZoneOffset.UTC),
					status.equals("COMPLETED")
							? OffsetDateTime.ofInstant(appliedAt.plusSeconds(7200), ZoneOffset.UTC) : null);
			if (status.equals("COMPLETED")) {
				match = jdbc.queryForObject("""
						insert into employment_match(employment_offer_id, completed_at) values (?, ?) returning id
						""", Long.class, offer,
						OffsetDateTime.ofInstant(appliedAt.plusSeconds(7200), ZoneOffset.UTC));
			}
		}
		return new ApplicationFixture(application, offer, match);
	}

	private long createPosting(long employer, String title) {
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '현재 사업장', '대표자', 'test/certificate.pdf', ?, 1,
				'서울 현재로 1', 37.1234567, 127.1234567, 'UNDER_5') returning id
				""", Long.class, employer, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		return jdbc.queryForObject("""
				insert into job_posting(workplace_id, title, wage_type, wage_amount, status)
				values (?, ?, 'MONTHLY', 3000000, 'RECRUITING') returning id
				""", Long.class, workplace, title);
	}

	private long createStudent(String name) {
		long student = createUser("STUDENT", name);
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level,
				visa_code, topik_level, profile_image_key)
				values (?, '2000-01-01', 'US', '서울', 'ADVANCED', 'D-4-1', 6, 'profiles/current.png')
				""", student);
		return student;
	}

	private long createUser(String role, String name) {
		return jdbc.queryForObject("insert into user_account(email, role, name) values (?, ?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role, name);
	}

	private HttpResponse<String> get(String token, String path) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(15)).GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private JsonNode body(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return mapper.readTree(response.body());
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
		JsonNode body = body(response, status);
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private record ApplicationFixture(long applicationId, Long offerId, Long matchId) {
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
