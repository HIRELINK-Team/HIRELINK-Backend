package com.hirelink.domain.jobposting;

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
import java.util.LinkedHashMap;
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
class JobPostingUpdateIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
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

	private long owner;
	private long workplace;
	private long posting;
	private String accessToken;

	@BeforeEach
	void prepareDraft() {
		jdbc.execute("TRUNCATE TABLE employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
		owner = createUser("EMPLOYER");
		workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '테스트 사업장', '테스트 대표자', 'test-documents/certificate.pdf', ?, 1,
				'테스트 주소', 37.1234567, 126.1234567, 'UNDER_5') returning id
				""", Long.class, owner, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		posting = jdbc.queryForObject("insert into job_posting(workplace_id, status) values (?, 'DRAFT') returning id", Long.class, workplace);
		accessToken = token(owner, "EMPLOYER");
	}

	@Test
	void updatesOnlyProvidedDraftFieldsAndPreservesUnrelatedData() throws Exception {
		jdbc.update("update job_posting set other_job_name = '기존 업종명', work_start_date = '2020-01-01' where id = ?", posting);
		Map<String, Object> before = jdbc.queryForMap("select * from job_posting where id = ?", posting);
		Map<String, Object> workplaceBefore = jdbc.queryForMap("select * from workplace where id = ?", workplace);
		HttpResponse<String> response = patch(Map.of("jobRole", "영화관 매표·안내", "businessTypeId", 8));
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("title").asString()).isEqualTo("테스트 사업장 영화관 매표·안내 모집");
		assertThat(body.path("businessTypeName").asString()).isEqualTo("OTHER");
		assertThat(body.path("workStartDate").asString()).isEqualTo("2020-01-01");
		assertThat(body.path("status").asString()).isEqualTo("DRAFT");
		assertThat(body.path("workTimes").isArray()).isTrue();
		assertThat(body.path("workTimes").size()).isZero();
		for (String field : List.of("recruitmentCount", "wageType", "wageAmount", "requiredKoreanLevel",
				"preferredExperience", "applicationDeadlineDate", "workTimeNegotiable")) {
			assertThat(body.path(field).isNull()).as(field).isTrue();
		}
		Map<String, Object> after = jdbc.queryForMap("select * from job_posting where id = ?", posting);
		for (String field : before.keySet()) {
			if (!List.of("title", "job_role", "business_type_id", "updated_at").contains(field)) {
				assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
			}
		}
		assertThat(jdbc.queryForMap("select * from workplace where id = ?", workplace)).isEqualTo(workplaceBefore);
		assertThat(body.propertyNames()).doesNotContain("otherJobName", "createdAt", "closedAt", "businessRegistrationFileKey");
	}

	@Test
	void publishesUsingSavedAndProvidedConditionsAndReturnsUpdatedValuesToReadApis() throws Exception {
		assertThat(patch(Map.of("jobRole", "매장 관리", "businessTypeId", 2)).statusCode()).isEqualTo(200);
		Map<String, Object> request = completeConditions();
		request.remove("jobRole");
		request.remove("businessTypeId");
		Instant before = Instant.now();
		HttpResponse<String> response = patch(request);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		JsonNode body = mapper.readTree(response.body());
		assertThat(Instant.parse(body.path("updatedAt").asString())).isBetween(before, Instant.now());
		Map<String, Object> expected = completeConditions();
		expected.put("jobPostingId", posting);
		expected.put("workplaceId", workplace);
		expected.put("workplaceName", "테스트 사업장");
		expected.put("title", "테스트 사업장 매장 관리 모집");
		expected.put("businessTypeName", "RETAIL");
		expected.put("updatedAt", body.path("updatedAt").asString());
		assertThat(body).isEqualTo(mapper.readTree(mapper.writeValueAsString(expected)));
		JsonNode detail = mapper.readTree(send("GET", "/api/job-postings/" + posting, accessToken, null).body());
		for (String field : completeConditions().keySet()) {
			assertThat(detail.path(field)).as(field).isEqualTo(body.path(field));
		}
		JsonNode list = mapper.readTree(send("GET", "/api/employers/me/job-postings", accessToken, null).body());
		assertThat(list.path("jobPostings").size()).isEqualTo(1);
		assertThat(list.path("jobPostings").get(0).path("status").asString()).isEqualTo("RECRUITING");
		assertThat(jdbc.queryForObject("select closed_at from job_posting where id = ?", Instant.class, posting)).isNull();
	}

	@Test
	void requiresEveryFinalConditionWhenPublishingAndPreservesDraftOnFailure() throws Exception {
		Map<String, Object> complete = completeConditions();
		Map<String, List<Map<String, Object>>> before = snapshot();
		for (String field : complete.keySet()) {
			if (field.equals("status")) {
				continue;
			}
			Map<String, Object> missing = new LinkedHashMap<>(complete);
			missing.remove(field);
			assertError(patch(missing), 400, "INVALID_INPUT");
			assertThat(snapshot()).as(field).isEqualTo(before);
		}
		complete.put("status", "DRAFT");
		assertThat(patch(complete).statusCode()).isEqualTo(200);
		assertThat(patch(Map.of("status", "RECRUITING")).statusCode()).isEqualTo(200);
	}

	@Test
	void appliesDateRulesToDraftPublicationAndOnlyProvidedRecruitingDates() throws Exception {
		Map<String, Object> conditions = completeConditions();
		String past = LocalDate.now(KOREA_ZONE).minusDays(1).toString();
		String today = LocalDate.now(KOREA_ZONE).toString();
		conditions.put("status", "DRAFT");
		conditions.put("workStartDate", past);
		conditions.put("applicationDeadlineDate", past);
		assertThat(patch(conditions).statusCode()).isEqualTo(200);
		Map<String, List<Map<String, Object>>> before = snapshot();
		assertError(patch(Map.of("status", "RECRUITING")), 400, "INVALID_INPUT");
		assertError(patch(Map.of("status", "RECRUITING", "workStartDate", today)), 400, "INVALID_INPUT");
		assertError(patch(Map.of("status", "RECRUITING", "applicationDeadlineDate", today)), 400, "INVALID_INPUT");
		assertThat(snapshot()).isEqualTo(before);
		assertThat(patch(Map.of("status", "RECRUITING", "workStartDate", today, "applicationDeadlineDate", today)).statusCode()).isEqualTo(200);

		jdbc.update("update job_posting set work_start_date = ?, application_deadline_date = ? where id = ?",
				LocalDate.parse(past), LocalDate.parse(past), posting);
		HttpResponse<String> wageOnly = patch(Map.of("wageAmount", 14000));
		assertThat(wageOnly.statusCode()).as(wageOnly.body()).isEqualTo(200);
		assertThat(mapper.readTree(wageOnly.body()).path("workStartDate").asString()).isEqualTo(past);
		assertThat(mapper.readTree(wageOnly.body()).path("applicationDeadlineDate").asString()).isEqualTo(past);
		before = snapshot();
		for (String field : List.of("workStartDate", "applicationDeadlineDate")) {
			assertError(patch(Map.of(field, past, "wageAmount", 15000)), 400, "INVALID_INPUT");
			assertThat(snapshot()).isEqualTo(before);
		}
		assertThat(patch(Map.of("workStartDate", LocalDate.now(KOREA_ZONE).plusDays(10).toString())).statusCode()).isEqualTo(200);
		assertThat(patch(Map.of("applicationDeadlineDate", today)).statusCode()).isEqualTo(200);
	}

	@Test
	void replacesAndClearsWorkTimesAndUpdatesTimestampForWorkTimeOnlyChange() throws Exception {
		assertThat(patch(Map.of("workTimes", List.of(time("FRI", "08:00", "12:00")))).statusCode()).isEqualTo(200);
		long oldTimeId = jdbc.queryForObject("select id from job_posting_work_time where job_posting_id = ?", Long.class, posting);
		Map<String, Object> postingBefore = jdbc.queryForMap("select * from job_posting where id = ?", posting);
		Instant before = Instant.now();
		List<Map<String, String>> times = List.of(time("TUE", "00:00", "04:00"), time("MON", "22:00", "00:00"),
				time("MON", "12:00", "14:00"), time("MON", "08:00", "12:00"));
		HttpResponse<String> response = patch(Map.of("workTimes", times));
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		JsonNode body = mapper.readTree(response.body());
		assertThat(Instant.parse(body.path("updatedAt").asString())).isBetween(before, Instant.now());
		List<Map<String, String>> sorted = List.of(time("MON", "08:00", "12:00"), time("MON", "12:00", "14:00"),
				time("MON", "22:00", "00:00"), time("TUE", "00:00", "04:00"));
		assertThat(body.path("workTimes")).isEqualTo(mapper.readTree(mapper.writeValueAsString(sorted)));
		assertThat(jdbc.queryForObject("select count(*) from job_posting_work_time where id = ?", Integer.class, oldTimeId)).isZero();
		Map<String, Object> after = jdbc.queryForMap("select * from job_posting where id = ?", posting);
		for (String field : postingBefore.keySet()) {
			if (!field.equals("updated_at")) {
				assertThat(after.get(field)).as(field).isEqualTo(postingBefore.get(field));
			}
		}
		assertThat(patch(Map.of("workTimes", List.of())).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from job_posting_work_time", Integer.class)).isZero();
		assertThat(patch(completeConditions()).statusCode()).isEqualTo(200);
		Map<String, List<Map<String, Object>>> recruitingBefore = snapshot();
		assertError(patch(Map.of("workTimes", List.of(), "workTimeNegotiable", true)), 400, "INVALID_INPUT");
		assertThat(snapshot()).isEqualTo(recruitingBefore);
	}

	@Test
	void rejectsInvalidInputWithoutChangingStoredData() throws Exception {
		Map<String, List<Map<String, Object>>> before = snapshot();
		for (String field : completeConditions().keySet()) {
			assertError(send("PATCH", "/api/job-postings/" + posting, accessToken, "{\"" + field + "\":null}"), 400, "INVALID_INPUT");
		}
		List<Map<String, Object>> invalid = List.of(
				Map.of("businessTypeId", "2"), Map.of("businessTypeId", 0), Map.of("businessTypeId", 1.5),
				Map.of("jobRole", "  "), Map.of("jobRole", 1), Map.of("jobRole", "가".repeat(101)),
				Map.of("recruitmentCount", 0), Map.of("recruitmentCount", 32768), Map.of("recruitmentCount", 1.5),
				Map.of("wageAmount", 0), Map.of("wageAmount", "14000"), Map.of("wageAmount", 2147483648L),
				Map.of("wageType", "DAILY"), Map.of("requiredKoreanLevel", "TOPIK_3"), Map.of("preferredExperience", "UNKNOWN"),
				Map.of("workStartDate", "2026-02-30"), Map.of("workStartDate", "2026-1-01"), Map.of("workStartDate", "0000-01-01"),
				Map.of("applicationDeadlineDate", "2026-11-01T00:00:00"), Map.of("applicationDeadlineDate", 20261101),
				Map.of("workTimeNegotiable", "false"), Map.of("status", "CLOSED"), Map.of("status", "draft"),
				Map.of("workplaceId", workplace), Map.of("title", "직접 입력한 제목"), Map.of("otherJobName", "영화관"),
				Map.of("workTimes", List.of(time("mon", "08:00", "12:00"))),
				Map.of("workTimes", List.of(time("MON", "08:15", "12:00"))),
				Map.of("workTimes", List.of(time("MON", "00:00", "00:00"))),
				Map.of("workTimes", List.of(time("MON", "22:00", "04:00"))),
				Map.of("workTimes", List.of(time("MON", "10:00", "14:00"), time("MON", "08:00", "12:00"))),
				Map.of("workTimes", List.of(Map.of("dayOfWeek", "MON", "startTime", "08:00", "endTime", "12:00", "id", 1))));
		for (Map<String, Object> request : invalid) {
			assertError(patch(request), 400, "INVALID_INPUT");
		}
		for (String json : List.of("{}", "[]", "null", "{\"workTimes\":[null]}", "{\"workTimes\":[{}]}")) {
			assertError(send("PATCH", "/api/job-postings/" + posting, accessToken, json), 400, "INVALID_INPUT");
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void blocksDowngradeAndAllClosedPostingChanges() throws Exception {
		assertThat(patch(completeConditions()).statusCode()).isEqualTo(200);
		Map<String, List<Map<String, Object>>> before = snapshot();
		assertError(patch(Map.of("status", "DRAFT", "wageAmount", 15000)), 409, "JOB_POSTING_STATE_CONFLICT");
		assertThat(snapshot()).isEqualTo(before);
		jdbc.update("update job_posting set status = 'CLOSED', closed_at = now() where id = ?", posting);
		before = snapshot();
		for (Map<String, Object> request : List.<Map<String, Object>>of(Map.of("status", "DRAFT"),
				Map.of("status", "RECRUITING"), Map.of("wageAmount", 15000))) {
			assertError(patch(request), 409, "JOB_POSTING_STATE_CONFLICT");
			assertThat(snapshot()).isEqualTo(before);
		}
	}

	@Test
	void requiresActiveEmployerOwnershipAndExistingPostingAndBusinessType() throws Exception {
		String path = "/api/job-postings/" + posting;
		String request = "{\"wageAmount\":15000}";
		Map<String, List<Map<String, Object>>> before = snapshot();
		assertError(send("PATCH", path, null, request), 401, "UNAUTHORIZED");
		assertError(send("PATCH", path, "invalid-token", request), 401, "UNAUTHORIZED");
		assertError(send("PATCH", path, token(99999, "EMPLOYER"), request), 401, "UNAUTHORIZED");
		assertError(send("PATCH", "/api/job-postings/99999", accessToken, request), 404, "JOB_POSTING_NOT_FOUND");
		assertError(send("PATCH", "/api/job-postings/abc", accessToken, request), 400, "INVALID_INPUT");
		assertError(send("PATCH", "/api/job-postings/9223372036854775808", accessToken, request), 400, "INVALID_INPUT");
		assertError(patch(Map.of("businessTypeId", 99999, "jobRole", "변경 직무")), 404, "BUSINESS_TYPE_NOT_FOUND");
		assertThat(snapshot()).isEqualTo(before);
		long other = createUser("EMPLOYER");
		long student = createUser("STUDENT");
		assertError(send("PATCH", path, token(other, "EMPLOYER"), request), 403, "FORBIDDEN");
		assertError(send("PATCH", path, token(student, "STUDENT"), request), 403, "FORBIDDEN");
		assertError(send("PATCH", path, token(student, "EMPLOYER"), request), 403, "FORBIDDEN");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", owner);
		before = snapshot();
		assertError(patch(Map.of("wageAmount", 15000)), 403, "WITHDRAWN_ACCOUNT");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void restoresOriginalPostingAndWorkTimesWhenReplacementSaveFails() throws Exception {
		assertThat(patch(completeConditions()).statusCode()).isEqualTo(200);
		Map<String, List<Map<String, Object>>> before = snapshot();
		jdbc.execute("alter table job_posting_work_time add constraint test_reject_tuesday check (day_of_week <> 'TUE')");
		try {
			HttpResponse<String> response = patch(Map.of("jobRole", "새로운 직무", "wageAmount", 15000,
					"workTimes", List.of(time("MON", "22:00", "00:00"), time("TUE", "00:00", "04:00"))));
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(snapshot()).isEqualTo(before);
		} finally {
			jdbc.execute("alter table job_posting_work_time drop constraint test_reject_tuesday");
		}
	}

	private Map<String, Object> completeConditions() {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("status", "RECRUITING");
		request.put("businessTypeId", 2);
		request.put("jobRole", "매장 관리");
		request.put("recruitmentCount", 2);
		request.put("wageType", "HOURLY");
		request.put("wageAmount", 13000);
		request.put("workStartDate", LocalDate.now(KOREA_ZONE).toString());
		request.put("requiredKoreanLevel", "INTERMEDIATE");
		request.put("preferredExperience", "NONE");
		request.put("applicationDeadlineDate", LocalDate.now(KOREA_ZONE).plusDays(14).toString());
		request.put("workTimeNegotiable", false);
		request.put("workTimes", List.of(time("MON", "08:00", "12:00")));
		return request;
	}

	private Map<String, String> time(String day, String start, String end) {
		return Map.of("dayOfWeek", day, "startTime", start, "endTime", end);
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
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

	private HttpResponse<String> patch(Map<String, Object> request) throws Exception {
		return send("PATCH", "/api/job-postings/" + posting, accessToken, mapper.writeValueAsString(request));
	}

	private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (json == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		} else {
			request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
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
