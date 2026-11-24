package com.hirelink.domain.employmentmatch;

import com.hirelink.domain.auth.support.LoginSessionFixtures;
import com.hirelink.support.PostgreSQLIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

import com.hirelink.domain.student.service.VisaExpirationSmsNotificationService;
import com.hirelink.global.sms.SmsSender;
import com.hirelink.global.storage.SupabaseStorageClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmploymentMatchDeletionIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@LocalServerPort private int port;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;
	@Autowired private JwtEncoder jwtEncoder;
	@Autowired private VisaExpirationSmsNotificationService visaNotifications;
	@MockitoBean private SupabaseStorageClient storageClient;
	@MockitoBean private SmsSender smsSender;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE student_enrollment_document, student_document_cleanup, employment_popup_notification, employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, sms_send_history, "
				+ "refresh_token, auth_account, student_registration_verification, user_account RESTART IDENTITY");
		reset(smsSender);
	}

	@Test
	void selectedDeletionIsIdempotentAndRemovesBothViewsContactAndVisaNotificationButKeepsHistoryLinks()
			throws Exception {
		LocalDate expiry = LocalDate.of(2027, 6, 30);
		long employer = createUser("EMPLOYER", "사업주", "01022222222");
		long posting = createPosting(employer);
		long student = createStudent("유학생", "01011111111", expiry);
		MatchFixture match = createMatch(student, posting);

		assertThat(deleteSelected(token(employer, "EMPLOYER"), posting, match.matchId()).statusCode())
				.isEqualTo(204);
		assertThat(deleteSelected(token(employer, "EMPLOYER"), posting, match.matchId()).statusCode())
				.isEqualTo(204);

		assertThat(jdbc.queryForObject("select deleted_at is not null from employment_match where id = ?",
				Boolean.class, match.matchId())).isTrue();
		assertThat(jdbc.queryForObject("select status from application where id = ?", String.class,
				match.applicationId())).isEqualTo("COMPLETED");
		assertThat(jdbc.queryForObject("select status from employment_offer where id = ?", String.class,
				match.offerId())).isEqualTo("ACCEPTED");
		assertThat(body(get(token(employer, "EMPLOYER"), "/api/matches/me?jobPostingId=" + posting), 200)
				.path("matches").isEmpty()).isTrue();
		assertThat(body(get(token(student, "STUDENT"), "/api/matches/me"), 200)
				.path("matches").isEmpty()).isTrue();
		assertError(get(token(employer, "EMPLOYER"), "/api/matches/" + match.matchId()),
				404, "EMPLOYMENT_MATCH_NOT_FOUND");
		assertError(get(token(student, "STUDENT"), "/api/matches/" + match.matchId()),
				404, "EMPLOYMENT_MATCH_NOT_FOUND");

		JsonNode application = body(get(token(student, "STUDENT"),
				"/api/applications/" + match.applicationId()), 200);
		assertThat(application.path("status").asString()).isEqualTo("COMPLETED");
		assertThat(application.path("offerStatus").asString()).isEqualTo("ACCEPTED");
		assertThat(application.path("matchId").asLong()).isEqualTo(match.matchId());

		visaNotifications.sendDueNotifications(expiry.minusWeeks(1));
		verify(smsSender).send(argThat(request -> request.recipient().equals("01011111111")));
		verify(smsSender, never()).send(argThat(request -> request.recipient().equals("01022222222")));
	}

	@Test
	void selectedDeletionRejectsOtherPostingMissingMatchAndOtherEmployerWithoutPartialChanges()
			throws Exception {
		long employer = createUser("EMPLOYER", "사업주", "01022222222");
		long otherEmployer = createUser("EMPLOYER", "다른 사업주", "01033333333");
		long firstPosting = createPosting(employer);
		long secondPosting = createPosting(employer);
		MatchFixture first = createMatch(createStudent("학생1", "01011111111", null), firstPosting);
		MatchFixture second = createMatch(createStudent("학생2", "01044444444", null), secondPosting);

		assertError(deleteSelected(token(employer, "EMPLOYER"), firstPosting,
				first.matchId(), second.matchId()), 403, "FORBIDDEN");
		assertThat(activeMatchCount()).isEqualTo(2);

		assertError(deleteSelected(token(employer, "EMPLOYER"), firstPosting,
				first.matchId(), 99999), 404, "EMPLOYMENT_MATCH_NOT_FOUND");
		assertThat(activeMatchCount()).isEqualTo(2);

		assertError(deleteSelected(token(otherEmployer, "EMPLOYER"), firstPosting,
				first.matchId()), 403, "FORBIDDEN");
		assertThat(activeMatchCount()).isEqualTo(2);
	}

	@Test
	void deleteAllAffectsOnlySelectedOwnedPostingAndCanBeRepeated() throws Exception {
		long employer = createUser("EMPLOYER", "사업주", "01022222222");
		long firstPosting = createPosting(employer);
		long secondPosting = createPosting(employer);
		MatchFixture first = createMatch(createStudent("학생1", "01011111111", null), firstPosting);
		MatchFixture second = createMatch(createStudent("학생2", "01033333333", null), firstPosting);
		MatchFixture untouched = createMatch(createStudent("학생3", "01044444444", null), secondPosting);

		assertThat(deleteAll(token(employer, "EMPLOYER"), firstPosting).statusCode()).isEqualTo(204);
		assertThat(deleteAll(token(employer, "EMPLOYER"), firstPosting).statusCode()).isEqualTo(204);

		assertThat(deleted(first.matchId())).isTrue();
		assertThat(deleted(second.matchId())).isTrue();
		assertThat(deleted(untouched.matchId())).isFalse();
		assertThat(body(get(token(employer, "EMPLOYER"),
				"/api/matches/me?jobPostingId=" + secondPosting), 200).path("matches").size()).isEqualTo(1);
		assertError(deleteAll(token(first.studentId(), "STUDENT"), secondPosting), 403, "FORBIDDEN");
		assertThat(deleted(untouched.matchId())).isFalse();
	}

	private MatchFixture createMatch(long student, long posting) throws Exception {
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 90.00, '{}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
		String snapshot = mapper.writeValueAsString(java.util.Map.of(
				"student", java.util.Map.of("name", "지원 당시 유학생"),
				"jobPosting", java.util.Map.of("jobPostingId", posting, "title", "공고",
						"workplaceName", "사업장"),
				"recommendation", java.util.Map.of("recommendationId", recommendation)));
		long application = jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, employer_response_deadline, applied_at)
				values (?, ?, ?, current_date, 1, cast(? as jsonb), 'COMPLETED', now(), now()) returning id
				""", Long.class, recommendation, student, posting, snapshot);
		long offer = jdbc.queryForObject("""
				insert into employment_offer(application_id, status, student_response_deadline, offered_at, responded_at)
				values (?, 'ACCEPTED', now(), now(), now()) returning id
				""", Long.class, application);
		long match = jdbc.queryForObject("""
				insert into employment_match(employment_offer_id, completed_at) values (?, now()) returning id
				""", Long.class, offer);
		return new MatchFixture(student, application, offer, match);
	}

	private long createPosting(long employer) {
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '사업장', '대표', 'key', ?, 1, '서울', 37.5, 127.0, '1-4') returning id
				""", Long.class, employer, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		return jdbc.queryForObject(
				"insert into job_posting(workplace_id, status) values (?, 'CLOSED') returning id",
				Long.class, workplace);
	}

	private long createStudent(String name, String phone, LocalDate expiry) {
		long student = createUser("STUDENT", name, phone);
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level,
				visa_code, stay_expiration_date) values (?, '2000-01-01', 'VN', '서울', 'INTERMEDIATE', 'D-2', ?)
				""", student, expiry);
		if (expiry != null) {
			jdbc.update("""
					insert into student_registration_verification(user_id, nationality, visa_code,
					stay_expires_on, card_verified_at, registered_at)
					values (?, 'VN', 'D-2', ?, now(), now())
					""", student, expiry);
			jdbc.update("update student_profile set onboarding_completed_at = now() where id = ?", student);
		}
		return student;
	}

	private long createUser(String role, String name, String phone) {
		return jdbc.queryForObject("""
				insert into user_account(email, role, name, phone_number, phone_verified_at)
				values (?, ?, ?, ?, now()) returning id
				""", Long.class, UUID.randomUUID() + "@example.com", role, name, phone);
	}

	private HttpResponse<String> deleteSelected(String token, long posting, long... matchIds) throws Exception {
		StringBuilder body = new StringBuilder("{\"matchIds\":[");
		for (int index = 0; index < matchIds.length; index++) {
			if (index > 0) body.append(',');
			body.append(matchIds[index]);
		}
		body.append("]}");
		return send(HttpRequest.newBuilder(uri("/api/job-postings/" + posting + "/matches"))
				.header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
				.method("DELETE", HttpRequest.BodyPublishers.ofString(body.toString())).build());
	}

	private HttpResponse<String> deleteAll(String token, long posting) throws Exception {
		return send(HttpRequest.newBuilder(uri("/api/job-postings/" + posting + "/matches/all"))
				.header("Authorization", "Bearer " + token)
				.method("DELETE", HttpRequest.BodyPublishers.noBody()).build());
	}

	private HttpResponse<String> get(String token, String path) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
		if (token != null) request.header("Authorization", "Bearer " + token);
		return send(request.build());
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private JsonNode body(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return mapper.readTree(response.body());
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		JsonNode body = body(response, status);
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private int activeMatchCount() {
		return jdbc.queryForObject("select count(*) from employment_match where deleted_at is null", Integer.class);
	}

	private boolean deleted(long matchId) {
		return jdbc.queryForObject("select deleted_at is not null from employment_match where id = ?",
				Boolean.class, matchId);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().claim("sid", LoginSessionFixtures.create(jdbc, userId)).issuer("hirelink")
				.subject(Long.toString(userId)).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600))
				.id(UUID.randomUUID().toString()).claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

	private record MatchFixture(long studentId, long applicationId, long offerId, long matchId) {
	}
}
