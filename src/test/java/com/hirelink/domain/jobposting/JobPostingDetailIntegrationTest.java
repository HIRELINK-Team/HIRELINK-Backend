package com.hirelink.domain.jobposting;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobPostingDetailIntegrationTest {

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
	void returnsStoredDetailsForOwnerAndStudentWithSortedTimesAndPreservesData() throws Exception {
		long owner = createUser("EMPLOYER");
		long student = createUser("STUDENT");
		long workplace = createWorkplace(owner);
		long posting = createPosting(workplace, "RECRUITING");
		long otherPosting = createPosting(workplace, "RECRUITING");
		addWorkTime(posting, "FRI", "16:00", "22:00");
		addWorkTime(posting, "MON", "22:00", "00:00");
		addWorkTime(posting, "MON", "08:00", "12:00");
		addWorkTime(posting, "TUE", "00:00", "04:00");
		addWorkTime(posting, "SUN", "17:00", "18:00");
		addWorkTime(otherPosting, "WED", "10:00", "14:00");
		Map<String, List<Map<String, Object>>> before = snapshot();
		JsonNode expected = mapper.readTree("""
				{
				  "jobPostingId": %d, "title": "테스트 채용공고", "status": "RECRUITING",
				  "workplaceId": %d, "workplaceName": "테스트 사업장", "workplaceAddress": "테스트 주소",
				  "latitude": 37.1234567, "longitude": 126.1234567,
				  "businessTypeId": 2, "businessTypeName": "RETAIL", "jobRole": "매장 관리",
				  "wageType": "HOURLY", "wageAmount": 13000, "recruitmentCount": 2,
				  "workStartDate": "2026-10-01", "requiredKoreanLevel": "INTERMEDIATE",
				  "preferredExperience": "NONE", "workTimeNegotiable": false,
				  "applicationDeadlineDate": "2026-10-01",
				  "workTimes": [
				    {"dayOfWeek":"MON","startTime":"08:00","endTime":"12:00"},
				    {"dayOfWeek":"MON","startTime":"22:00","endTime":"00:00"},
				    {"dayOfWeek":"TUE","startTime":"00:00","endTime":"04:00"},
				    {"dayOfWeek":"FRI","startTime":"16:00","endTime":"22:00"},
				    {"dayOfWeek":"SUN","startTime":"17:00","endTime":"18:00"}
				  ]
				}
				""".formatted(posting, workplace));
		for (String accessToken : List.of(token(owner, "EMPLOYER"), token(student, "STUDENT"))) {
			HttpResponse<String> response = get(accessToken, Long.toString(posting));
			assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
			assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
			assertThat(mapper.readTree(response.body())).isEqualTo(expected);
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void returnsOwnerDraftNullsWithoutFallingBackToWorkplaceAndAllowsClosedPosting() throws Exception {
		long owner = createUser("EMPLOYER");
		long workplace = createWorkplace(owner);
		long draft = createPosting(workplace, "DRAFT");
		long closed = createPosting(workplace, "CLOSED");
		Map<String, List<Map<String, Object>>> before = snapshot();
		String accessToken = token(owner, "EMPLOYER");
		HttpResponse<String> response = get(accessToken, Long.toString(draft));
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("status").asString()).isEqualTo("DRAFT");
		assertThat(body.path("workplaceId").asLong()).isEqualTo(workplace);
		for (String field : List.of("title", "businessTypeId", "businessTypeName", "jobRole", "wageType", "wageAmount",
				"recruitmentCount", "workStartDate", "requiredKoreanLevel", "preferredExperience", "workTimeNegotiable",
				"applicationDeadlineDate")) {
			assertThat(body.path(field).isNull()).as(field).isTrue();
		}
		assertThat(body.path("workTimes").isArray()).isTrue();
		assertThat(body.path("workTimes").size()).isZero();
		response = get(accessToken, Long.toString(closed));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(response.body()).path("status").asString()).isEqualTo("CLOSED");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void enforcesOwnershipAndRecruitingStatusUsingCurrentRoleAndRejectsWithdrawnAccounts() throws Exception {
		long owner = createUser("EMPLOYER");
		long otherEmployer = createUser("EMPLOYER");
		long student = createUser("STUDENT");
		long workplace = createWorkplace(owner);
		for (String status : List.of("DRAFT", "RECRUITING", "CLOSED")) {
			long posting = createPosting(workplace, status);
			String id = Long.toString(posting);
			assertError(get(token(otherEmployer, "EMPLOYER"), id), 403, "FORBIDDEN");
			assertError(get(token(otherEmployer, "STUDENT"), id), 403, "FORBIDDEN");
			for (String claimedRole : List.of("STUDENT", "EMPLOYER")) {
				HttpResponse<String> response = get(token(student, claimedRole), id);
				if (status.equals("RECRUITING")) {
					assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
				} else {
					assertError(response, 403, "FORBIDDEN");
				}
			}
		}
		long recruiting = createPosting(workplace, "RECRUITING");
		jdbc.update("update user_account set withdrawn_at = now() where id in (?, ?)", owner, student);
		Map<String, List<Map<String, Object>>> before = snapshot();
		assertError(get(token(owner, "EMPLOYER"), Long.toString(recruiting)), 403, "WITHDRAWN_ACCOUNT");
		assertError(get(token(student, "STUDENT"), Long.toString(recruiting)), 403, "WITHDRAWN_ACCOUNT");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void returnsAuthenticationNotFoundAndInvalidIdErrors() throws Exception {
		long owner = createUser("EMPLOYER");
		long student = createUser("STUDENT");
		long posting = createPosting(createWorkplace(owner), "RECRUITING");
		String accessToken = token(owner, "EMPLOYER");
		Map<String, List<Map<String, Object>>> before = snapshot();
		assertError(get(null, Long.toString(posting)), 401, "UNAUTHORIZED");
		assertError(get("invalid-token", Long.toString(posting)), 401, "UNAUTHORIZED");
		assertError(get(token(99999, "EMPLOYER"), Long.toString(posting)), 401, "UNAUTHORIZED");
		assertError(get(accessToken, "99999"), 404, "JOB_POSTING_NOT_FOUND");
		assertError(get(token(student, "STUDENT"), "99999"), 404, "JOB_POSTING_NOT_FOUND");
		for (String id : List.of("invalid", "1.5", "9223372036854775808")) {
			assertError(get(accessToken, id), 400, "INVALID_INPUT");
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void returnsGenericErrorWhenDatabaseReadFails() throws Exception {
		long owner = createUser("EMPLOYER");
		long posting = createPosting(createWorkplace(owner), "RECRUITING");
		Map<String, List<Map<String, Object>>> before = snapshot();
		jdbc.execute("alter table job_posting rename column job_role to test_unavailable_job_role");
		try {
			HttpResponse<String> response = get(token(owner, "EMPLOYER"), Long.toString(posting));
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

	private long createWorkplace(long userId) {
		return jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '테스트 사업장', '테스트 대표자', 'test-documents/certificate.pdf', ?, 1,
				'테스트 주소', 37.1234567, 126.1234567, 'UNDER_5') returning id
				""", Long.class, userId, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
	}

	private long createPosting(long workplaceId, String status) {
		long id = jdbc.queryForObject("insert into job_posting(workplace_id, status) values (?, ?) returning id",
				Long.class, workplaceId, status);
		if (!status.equals("DRAFT")) {
			jdbc.update("""
					update job_posting set business_type_id = 2, title = '테스트 채용공고', job_role = '매장 관리',
					wage_type = 'HOURLY', wage_amount = 13000, recruitment_count = 2, work_start_date = '2026-10-01',
					required_korean_level = 'INTERMEDIATE', preferred_experience = 'NONE', work_time_negotiable = false,
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

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> get(String token, String postingId) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/job-postings/" + postingId))
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
