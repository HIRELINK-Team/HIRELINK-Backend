package com.hirelink.domain.jobposting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobPostingListIntegrationTest {

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
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearDatabase() {
		jdbc.execute("TRUNCATE TABLE employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void returnsOnlyOwnedNonDraftPostingsWithOrderedWorkTimesAndLeavesDataUnchanged() throws Exception {
		long userId = createUser("EMPLOYER");
		long otherUserId = createUser("EMPLOYER");
		long firstWorkplace = createWorkplace(userId, "첫 번째 사업장");
		long secondWorkplace = createWorkplace(userId, "두 번째 사업장");
		long otherWorkplace = createWorkplace(otherUserId, "다른 사업장");
		long first = createPosting(firstWorkplace, "RECRUITING", "2026-10-23T06:00:00Z");
		long second = createPosting(secondWorkplace, "CLOSED", "2026-10-25T06:00:00Z");
		long third = createPosting(firstWorkplace, "RECRUITING", "2026-10-23T06:00:00Z");
		createPosting(firstWorkplace, "DRAFT", "2026-10-27T06:00:00Z");
		long other = createPosting(otherWorkplace, "RECRUITING", "2026-10-28T06:00:00Z");
		jdbc.update("update job_posting set wage_type = 'MONTHLY', wage_amount = 2000000 where id = ?", second);
		addWorkTime(first, "FRI", "16:00", "22:00");
		addWorkTime(first, "MON", "22:00", "00:00");
		addWorkTime(first, "SUN", "17:00", "18:00");
		addWorkTime(first, "MON", "08:00", "12:00");
		addWorkTime(first, "TUE", "00:00", "04:00");
		addWorkTime(other, "WED", "01:00", "02:00");
		Map<String, List<Map<String, Object>>> before = snapshot();

		HttpResponse<String> response = get(token(userId, "EMPLOYER"), "");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(postingIds(response)).containsExactly(second, third, first);
		JsonNode root = mapper.readTree(response.body());
		assertThat(root.propertyNames()).containsExactly("jobPostings");
		JsonNode postings = root.path("jobPostings");
		for (JsonNode posting : postings) {
			assertThat(posting.propertyNames()).containsExactlyInAnyOrder("jobPostingId", "workplaceId", "workplaceName",
					"jobRole", "wageType", "wageAmount", "status", "applicationDeadlineDate", "workTimes", "createdAt");
		}
		assertThat(postings.get(0).path("workplaceId").asLong()).isEqualTo(secondWorkplace);
		assertThat(postings.get(0).path("workplaceName").asString()).isEqualTo("두 번째 사업장");
		assertThat(postings.get(0).path("wageType").asString()).isEqualTo("MONTHLY");
		assertThat(postings.get(0).path("wageAmount").asInt()).isEqualTo(2000000);
		assertThat(postings.get(0).path("status").asString()).isEqualTo("CLOSED");
		JsonNode firstPosting = postings.get(2);
		assertThat(firstPosting.path("workplaceId").asLong()).isEqualTo(firstWorkplace);
		assertThat(firstPosting.path("workplaceName").asString()).isEqualTo("첫 번째 사업장");
		assertThat(firstPosting.path("jobRole").asString()).isEqualTo("바리스타");
		assertThat(firstPosting.path("wageType").asString()).isEqualTo("HOURLY");
		assertThat(firstPosting.path("wageAmount").asInt()).isEqualTo(13000);
		assertThat(firstPosting.path("status").asString()).isEqualTo("RECRUITING");
		assertThat(firstPosting.path("applicationDeadlineDate").asString()).isEqualTo("2026-10-01");
		assertThat(Instant.parse(firstPosting.path("createdAt").asString())).isEqualTo(Instant.parse("2026-10-23T06:00:00Z"));
		assertThat(firstPosting.path("workTimes")).isEqualTo(mapper.readTree("""
				[{"dayOfWeek":"MON","startTime":"08:00","endTime":"12:00"},
				 {"dayOfWeek":"MON","startTime":"22:00","endTime":"00:00"},
				 {"dayOfWeek":"TUE","startTime":"00:00","endTime":"04:00"},
				 {"dayOfWeek":"FRI","startTime":"16:00","endTime":"22:00"},
				 {"dayOfWeek":"SUN","startTime":"17:00","endTime":"18:00"}]
				"""));
		assertThat(postingIds(get(token(userId, "EMPLOYER"), "?employerUserId=" + otherUserId)))
				.containsExactly(second, third, first);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void appliesStatusBeforeIncludeDraftAndReturnsDraftNullsAndEmptyWorkTimes() throws Exception {
		long userId = createUser("EMPLOYER");
		long workplaceId = createWorkplace(userId, "테스트 사업장");
		long draft = createPosting(workplaceId, "DRAFT", "2026-10-25T06:00:00Z");
		long closed = createPosting(workplaceId, "CLOSED", "2026-10-24T06:00:00Z");
		long recruiting = createPosting(workplaceId, "RECRUITING", "2026-10-23T06:00:00Z");
		Map<String, List<Map<String, Object>>> before = snapshot();
		String token = token(userId, "EMPLOYER");
		Map<String, List<Long>> cases = Map.of(
				"", List.of(closed, recruiting),
				"?includeDraft=false", List.of(closed, recruiting),
				"?includeDraft=true", List.of(draft, closed, recruiting),
				"?status=RECRUITING", List.of(recruiting),
				"?status=CLOSED", List.of(closed),
				"?status=RECRUITING&includeDraft=true", List.of(recruiting),
				"?status=CLOSED&includeDraft=true", List.of(closed),
				"?status=RECRUITING&includeDraft=false", List.of(recruiting));
		for (var entry : cases.entrySet()) {
			HttpResponse<String> response = get(token, entry.getKey());
			assertThat(response.statusCode()).as(entry.getKey() + ": " + response.body()).isEqualTo(200);
			assertThat(postingIds(response)).containsExactlyElementsOf(entry.getValue());
		}
		JsonNode draftResponse = mapper.readTree(get(token, "?includeDraft=true").body()).path("jobPostings").get(0);
		assertThat(draftResponse.path("status").asString()).isEqualTo("DRAFT");
		for (String field : List.of("jobRole", "wageType", "wageAmount", "applicationDeadlineDate")) {
			assertThat(draftResponse.path(field).isNull()).as(field).isTrue();
		}
		assertThat(draftResponse.path("workTimes").isArray()).isTrue();
		assertThat(draftResponse.path("workTimes").size()).isZero();
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void returnsEmptyListWhenThereAreNoOwnedPostingsOrNoMatchingStatus() throws Exception {
		long userId = createUser("EMPLOYER");
		long otherUserId = createUser("EMPLOYER");
		createPosting(createWorkplace(otherUserId, "다른 사업장"), "RECRUITING", "2026-10-25T06:00:00Z");
		String token = token(userId, "EMPLOYER");
		HttpResponse<String> response = get(token, "?includeDraft=true");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(response.body())).isEqualTo(mapper.readTree("{\"jobPostings\":[]}"));
		long workplace = createWorkplace(userId, "내 사업장");
		createPosting(workplace, "DRAFT", "2026-10-26T06:00:00Z");
		Map<String, List<Map<String, Object>>> before = snapshot();
		for (String query : List.of("", "?status=RECRUITING", "?status=CLOSED&includeDraft=true")) {
			response = get(token, query);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(mapper.readTree(response.body())).isEqualTo(mapper.readTree("{\"jobPostings\":[]}"));
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void rejectsInvalidFiltersWithoutChangingData() throws Exception {
		long userId = createUser("EMPLOYER");
		createPosting(createWorkplace(userId, "테스트 사업장"), "DRAFT", "2026-10-25T06:00:00Z");
		String token = token(userId, "EMPLOYER");
		Map<String, List<Map<String, Object>>> before = snapshot();
		for (String query : List.of("?status=DRAFT", "?status=UNKNOWN", "?status=recruiting", "?status=",
				"?includeDraft=", "?includeDraft=1", "?includeDraft=yes", "?includeDraft=TRUE", "?includeDraft=null",
				"?status=RECRUITING&includeDraft=invalid", "?status=invalid&includeDraft=true")) {
			assertError(get(token, query), 400, "INVALID_INPUT");
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void requiresAuthenticationAndCurrentEmployerRoleAndRejectsWithdrawnAccounts() throws Exception {
		long userId = createUser("STUDENT");
		assertError(get(null, ""), 401, "UNAUTHORIZED");
		assertError(get("invalid-token", ""), 401, "UNAUTHORIZED");
		assertError(get(token(999, "EMPLOYER"), ""), 401, "UNAUTHORIZED");
		assertError(get(token(userId, "STUDENT"), ""), 403, "FORBIDDEN");
		assertError(get(token(userId, "EMPLOYER"), ""), 403, "FORBIDDEN");
		jdbc.update("update user_account set role = 'EMPLOYER', withdrawn_at = now() where id = ?", userId);
		assertError(get(token(userId, "EMPLOYER"), ""), 403, "WITHDRAWN_ACCOUNT");
	}

	@Test
	void returnsGenericServerErrorWhenDatabaseReadFails() throws Exception {
		long userId = createUser("EMPLOYER");
		createPosting(createWorkplace(userId, "테스트 사업장"), "RECRUITING", "2026-10-25T06:00:00Z");
		Map<String, List<Map<String, Object>>> before = snapshot();
		jdbc.execute("alter table job_posting rename column job_role to test_unavailable_job_role");
		try {
			HttpResponse<String> response = get(token(userId, "EMPLOYER"), "");
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
		} finally {
			jdbc.execute("alter table job_posting rename column test_unavailable_job_role to job_role");
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private long createWorkplace(long userId, String name) {
		return jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, ?, '테스트 대표자', 'test-documents/certificate.pdf', ?, 1,
				'테스트 주소', 37.1234567, 126.1234567, 'UNDER_5') returning id
				""", Long.class, userId, name, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
	}

	private long createPosting(long workplaceId, String status, String createdAt) {
		Timestamp timestamp = Timestamp.from(Instant.parse(createdAt));
		long id = jdbc.queryForObject("""
				insert into job_posting(workplace_id, status, created_at, updated_at) values (?, ?, ?, ?) returning id
				""", Long.class, workplaceId, status, timestamp, timestamp);
		if (!status.equals("DRAFT")) {
			jdbc.update("""
					update job_posting set job_role = '바리스타', wage_type = 'HOURLY', wage_amount = 13000,
					application_deadline_date = '2026-10-01' where id = ?
					""", id);
		}
		return id;
	}

	private void addWorkTime(long postingId, String day, String start, String end) {
		jdbc.update("insert into job_posting_work_time(job_posting_id, day_of_week, start_time, end_time) values (?, ?, ?::time, ?::time)",
				postingId, day, start, end);
	}

	private Map<String, List<Map<String, Object>>> snapshot() {
		return Map.of("postings", jdbc.queryForList("select * from job_posting order by id"),
				"times", jdbc.queryForList("select * from job_posting_work_time order by id"),
				"workplaces", jdbc.queryForList("select * from workplace order by id"),
				"users", jdbc.queryForList("select * from user_account order by id"));
	}

	private List<Long> postingIds(HttpResponse<String> response) {
		List<Long> ids = new ArrayList<>();
		for (JsonNode posting : mapper.readTree(response.body()).path("jobPostings")) {
			ids.add(posting.path("jobPostingId").asLong());
		}
		return ids;
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> get(String token, String query) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/employers/me/job-postings" + query))
				.timeout(Duration.ofSeconds(15)).GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
