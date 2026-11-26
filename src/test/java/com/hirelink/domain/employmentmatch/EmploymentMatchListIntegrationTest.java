package com.hirelink.domain.employmentmatch;

import com.hirelink.domain.auth.support.LoginSessionFixtures;
import com.hirelink.support.PostgreSQLIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.hirelink.global.storage.SupabaseStorageClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmploymentMatchListIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final Instant BASE_TIME = Instant.parse("2026-11-01T01:00:00Z");
	private static final Instant QUERY_TIME = Instant.parse("2030-01-31T00:00:00Z");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
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
	@MockitoBean
	private Clock clock;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		when(clock.instant()).thenReturn(QUERY_TIME);
		jdbc.execute("TRUNCATE TABLE student_enrollment_document, student_document_cleanup, employment_popup_notification, employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, student_registration_verification, user_account RESTART IDENTITY");
	}

	@Test
	void employerListMasksAtAndAfterDeadlineWithoutFinalizationOrStateChanges() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "공고");
		long student = createStudent("현재 유학생");
		MatchFixture fixture = createMatch(student, posting, "지원 당시 유학생", BASE_TIME);
		when(storageClient.createSignedUrl("profiles/current.png")).thenReturn("https://storage.example/profile");
		Map<String, Object> applicationBefore = jdbc.queryForMap("select * from application where id = ?", fixture.applicationId());
		Map<String, Object> offerBefore = jdbc.queryForMap("select * from employment_offer where id = ?", fixture.offerId());
		Map<String, Object> matchBefore = jdbc.queryForMap("select * from employment_match where id = ?", fixture.matchId());

		setWithdrawal(student, QUERY_TIME.minus(Duration.ofDays(30)).plusSeconds(1));
		JsonNode visible = firstMatch(get(token(employer, "EMPLOYER"), "/api/matches/me"));
		assertThat(visible.path("studentDataMasked").asBoolean()).isFalse();
		assertThat(visible.path("student").path("name").asString()).isEqualTo("지원 당시 유학생");
		assertThat(visible.path("desiredStartDate").asString()).isEqualTo("2026-11-10");

		setWithdrawal(student, QUERY_TIME.minus(Duration.ofDays(30)));
		clearInvocations(storageClient);
		assertMaskedListItem(firstMatch(get(token(employer, "EMPLOYER"), "/api/matches/me")), fixture);
		assertThat(jdbc.queryForObject("select withdrawal_finalized_at from user_account where id = ?", Object.class,
				student)).isNull();

		setWithdrawal(student, QUERY_TIME.minus(Duration.ofDays(30)).minusSeconds(1));
		assertMaskedListItem(firstMatch(get(token(employer, "EMPLOYER"), "/api/matches/me")), fixture);
		verifyNoInteractions(storageClient);
		assertThat(jdbc.queryForMap("select * from application where id = ?", fixture.applicationId())).isEqualTo(applicationBefore);
		assertThat(jdbc.queryForMap("select * from employment_offer where id = ?", fixture.offerId())).isEqualTo(offerBefore);
		assertThat(jdbc.queryForMap("select * from employment_match where id = ?", fixture.matchId())).isEqualTo(matchBefore);
	}

	@Test
	void employerListsOwnMatchesFromSnapshotWithoutContactAndCanFilterByPosting() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long firstPosting = createPosting(employer, "현재 첫 공고");
		long secondPosting = createPosting(employer, "현재 둘째 공고");
		MatchFixture first = createMatch(createStudent("현재 학생1"), firstPosting, "지원 당시 학생1", BASE_TIME);
		MatchFixture second = createMatch(createStudent("현재 학생2"), secondPosting, "지원 당시 학생2",
				BASE_TIME.plusSeconds(60));
		when(storageClient.createSignedUrl("profiles/current.png"))
				.thenReturn("https://storage.example.com/current-profile");

		JsonNode body = body(get(token(employer, "EMPLOYER"), "/api/matches/me"), 200);

		JsonNode matches = body.path("matches");
		assertThat(matches.size()).isEqualTo(2);
		assertThat(matches.get(0).path("matchId").asLong()).isEqualTo(second.matchId());
		assertThat(matches.get(1).path("matchId").asLong()).isEqualTo(first.matchId());
		assertThat(matches.get(0).path("jobTitle").asString()).isEqualTo("지원 당시 공고");
		assertThat(matches.get(0).path("workplaceName").asString()).isEqualTo("지원 당시 사업장");
		assertThat(matches.get(0).path("student").path("name").asString()).isEqualTo("지원 당시 학생2");
		assertThat(matches.get(0).path("student").path("profileImageUrl").asString())
				.isEqualTo("https://storage.example.com/current-profile");
		assertThat(body.toString()).doesNotContain("phone", "contact");

		JsonNode filtered = body(get(token(employer, "EMPLOYER"),
				"/api/matches/me?jobPostingId=" + firstPosting), 200);
		assertThat(filtered.path("matches").size()).isEqualTo(1);
		assertThat(filtered.path("matches").get(0).path("matchId").asLong()).isEqualTo(first.matchId());
	}

	@Test
	void studentListsOnlyOwnMatchesWithNullStudentAndCannotUsePostingFilter() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long posting = createPosting(employer, "공고");
		long student = createStudent("학생");
		MatchFixture own = createMatch(student, posting, "지원 당시 학생", BASE_TIME);
		createMatch(createStudent("다른 학생"), posting, "다른 학생", BASE_TIME.plusSeconds(60));

		JsonNode body = body(get(token(student, "STUDENT"), "/api/matches/me"), 200);

		assertThat(body.path("matches").size()).isEqualTo(1);
		assertThat(body.path("matches").get(0).path("matchId").asLong()).isEqualTo(own.matchId());
		assertThat(body.path("matches").get(0).path("student").isNull()).isTrue();
		assertThat(body.toString()).doesNotContain("phone", "contact");
		assertError(get(token(student, "STUDENT"),
				"/api/matches/me?jobPostingId=" + posting), 400, "INVALID_INPUT");
	}

	@Test
	void matchListValidatesAuthenticationPostingExistenceAndOwnership() throws Exception {
		long employer = createUser("EMPLOYER", "사업주");
		long otherEmployer = createUser("EMPLOYER", "다른 사업주");
		long posting = createPosting(employer, "공고");

		assertError(get(null, "/api/matches/me"), 401, "UNAUTHORIZED");
		assertError(get(token(employer, "EMPLOYER"),
				"/api/matches/me?jobPostingId=99999"), 404, "JOB_POSTING_NOT_FOUND");
		assertError(get(token(otherEmployer, "EMPLOYER"),
				"/api/matches/me?jobPostingId=" + posting), 403, "FORBIDDEN");
		assertError(get(token(employer, "EMPLOYER"),
				"/api/matches/me?jobPostingId=0"), 400, "INVALID_INPUT");
	}

	private MatchFixture createMatch(long student, long posting, String snapshotName, Instant completedAt)
			throws Exception {
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 90.00, '{}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
		String snapshot = """
				{
				  "version":1,
				  "student":{"studentProfileId":%d,"name":"%s","nationality":"VN","visaCode":"D-2-2","topikLevel":4},
				  "jobPosting":{"jobPostingId":%d,"title":"지원 당시 공고","workplaceName":"지원 당시 사업장",
				    "wageType":"HOURLY","wageAmount":12500},
				  "recommendation":{"recommendationId":%d}
				}
				""".formatted(student, snapshotName, posting, recommendation);
		long application = jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, employer_response_deadline, applied_at)
				values (?, ?, ?, '2026-11-10', 1, cast(? as jsonb), 'COMPLETED', ?, ?) returning id
				""", Long.class, recommendation, student, posting, snapshot,
				OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC),
				OffsetDateTime.ofInstant(completedAt.minusSeconds(7200), ZoneOffset.UTC));
		long offer = jdbc.queryForObject("""
				insert into employment_offer(application_id, status, student_response_deadline, offered_at, responded_at)
				values (?, 'ACCEPTED', ?, ?, ?) returning id
				""", Long.class, application,
				OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC),
				OffsetDateTime.ofInstant(completedAt.minusSeconds(3600), ZoneOffset.UTC),
				OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC));
		long match = jdbc.queryForObject("""
				insert into employment_match(employment_offer_id, completed_at) values (?, ?) returning id
				""", Long.class, offer, OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC));
		return new MatchFixture(application, offer, match);
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
				values (?, ?, 'MONTHLY', 3000000, 'CLOSED') returning id
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

	private JsonNode firstMatch(HttpResponse<String> response) {
		return body(response, 200).path("matches").get(0);
	}

	private void assertMaskedListItem(JsonNode item, MatchFixture fixture) {
		assertThat(item.path("studentDataMasked").asBoolean()).isTrue();
		assertThat(item.path("student").isNull()).isTrue();
		assertThat(item.path("desiredStartDate").isNull()).isTrue();
		assertThat(item.path("matchId").asLong()).isEqualTo(fixture.matchId());
		assertThat(item.path("applicationId").asLong()).isEqualTo(fixture.applicationId());
		assertThat(item.path("offerId").asLong()).isEqualTo(fixture.offerId());
		assertThat(item.path("matchedAt").isTextual()).isTrue();
		assertThat(item.path("jobTitle").asString()).isEqualTo("지원 당시 공고");
	}

	private void setWithdrawal(long student, Instant requestedAt) {
		jdbc.update("update user_account set withdrawn_at = ?, withdrawal_requested_at = ?, withdrawal_finalized_at = null where id = ?",
				OffsetDateTime.ofInstant(requestedAt, ZoneOffset.UTC),
				OffsetDateTime.ofInstant(requestedAt, ZoneOffset.UTC), student);
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
		JwtClaimsSet claims = JwtClaimsSet.builder().claim("sid", LoginSessionFixtures.create(jdbc, userId)).issuer("hirelink").subject(Long.toString(userId))
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

	private record MatchFixture(long applicationId, long offerId, long matchId) {
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
