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
class JobPostingCreateIntegrationTest {

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

	@BeforeEach
	void clearDatabase() {
		jdbc.execute("TRUNCATE TABLE employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void createsRecruitingPostingAndWorkTimesAndConnectsToListAndDetail() throws Exception {
		long owner = createUser("EMPLOYER");
		long workplace = createWorkplace(owner, "테스트 사업장");
		String accessToken = token(owner, "EMPLOYER");
		Map<String, Object> request = recruitingRequest(workplace);
		Map<String, Object> workplaceBefore = jdbc.queryForMap("select * from workplace where id = ?", workplace);
		Instant before = Instant.now();
		HttpResponse<String> response = create(accessToken, request);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		long posting = body.path("jobPostingId").asLong();
		assertThat(posting).isPositive();
		assertThat(Instant.parse(body.path("createdAt").asString())).isBetween(before, Instant.now());
		List<Map<String, String>> sortedTimes = List.of(time("MON", "08:00", "12:00"), time("MON", "12:00", "14:00"),
				time("MON", "22:00", "00:00"), time("TUE", "00:00", "04:00"), time("FRI", "16:00", "22:00"));
		Map<String, Object> expected = new LinkedHashMap<>(request);
		expected.put("jobPostingId", posting);
		expected.put("workplaceName", "테스트 사업장");
		expected.put("title", "테스트 사업장 매장 관리 모집");
		expected.put("businessTypeName", "RETAIL");
		expected.put("createdAt", body.path("createdAt").asString());
		expected.put("workTimes", sortedTimes);
		assertThat(body).isEqualTo(mapper.readTree(mapper.writeValueAsString(expected)));

		Map<String, Object> saved = jdbc.queryForMap("select * from job_posting where id = ?", posting);
		assertThat(saved.get("workplace_id")).isEqualTo(workplace);
		assertThat(saved.get("business_type_id")).isEqualTo(2L);
		assertThat(saved.get("title")).isEqualTo("테스트 사업장 매장 관리 모집");
		assertThat(saved.get("status")).isEqualTo("RECRUITING");
		assertThat(saved.get("created_at")).isNotNull();
		assertThat(saved.get("updated_at")).isNotNull();
		assertThat(saved.get("closed_at")).isNull();
		assertThat(saved.get("other_job_name")).isNull();
		assertThat(jdbc.queryForObject("select count(*) from job_posting_work_time where job_posting_id = ?", Integer.class, posting)).isEqualTo(5);
		HttpResponse<String> detail = send("GET", "/api/job-postings/" + posting, accessToken, null);
		assertThat(detail.statusCode()).isEqualTo(200);
		JsonNode detailBody = mapper.readTree(detail.body());
		for (String field : List.of("title", "businessTypeId", "businessTypeName", "jobRole", "recruitmentCount", "wageType",
				"wageAmount", "workStartDate", "requiredKoreanLevel", "preferredExperience", "applicationDeadlineDate",
				"workTimeNegotiable", "status", "workTimes")) {
			assertThat(detailBody.path(field)).as(field).isEqualTo(body.path(field));
		}
		JsonNode list = mapper.readTree(send("GET", "/api/employers/me/job-postings", accessToken, null).body());
		assertThat(list.path("jobPostings").size()).isEqualTo(1);
		assertThat(list.path("jobPostings").get(0).path("jobPostingId").asLong()).isEqualTo(posting);
		assertThat(list.path("jobPostings").get(0).path("workTimes")).isEqualTo(body.path("workTimes"));
		assertThat(jdbc.queryForMap("select * from workplace where id = ?", workplace)).isEqualTo(workplaceBefore);
	}

	@Test
	void createsNewDraftsWithMissingOrNullValuesAndKeepsProvidedPastDates() throws Exception {
		long owner = createUser("EMPLOYER");
		long workplace = createWorkplace(owner, "테스트 사업장");
		String accessToken = token(owner, "EMPLOYER");
		Map<String, Object> request = draftRequest(workplace);
		HttpResponse<String> first = create(accessToken, request);
		assertThat(first.statusCode()).isEqualTo(201);
		JsonNode body = mapper.readTree(first.body());
		long firstId = body.path("jobPostingId").asLong();
		for (String field : List.of("title", "businessTypeId", "businessTypeName", "jobRole", "recruitmentCount", "wageType",
				"wageAmount", "workStartDate", "requiredKoreanLevel", "preferredExperience", "applicationDeadlineDate", "workTimeNegotiable")) {
			assertThat(body.path(field).isNull()).as(field).isTrue();
			if (!List.of("title", "businessTypeName").contains(field)) {
				request.put(field, null);
			}
		}
		assertThat(body.path("workTimes").isArray()).isTrue();
		assertThat(body.path("workTimes").size()).isZero();
		request.put("workTimes", null);
		HttpResponse<String> second = create(accessToken, request);
		assertThat(second.statusCode()).isEqualTo(201);
		assertThat(mapper.readTree(second.body()).path("jobPostingId").asLong()).isNotEqualTo(firstId);
		request.put("jobRole", "매장 관리");
		request.put("businessTypeId", 8);
		request.put("wageType", "MONTHLY");
		request.put("wageAmount", 1);
		request.put("recruitmentCount", 32767);
		request.put("workTimeNegotiable", true);
		request.put("workStartDate", "2020-01-01");
		request.put("applicationDeadlineDate", "2019-12-31");
		request.put("workTimes", List.of());
		HttpResponse<String> partial = create(accessToken, request);
		assertThat(partial.statusCode()).as(partial.body()).isEqualTo(201);
		body = mapper.readTree(partial.body());
		assertThat(body.path("title").asString()).isEqualTo("테스트 사업장 매장 관리 모집");
		assertThat(body.path("businessTypeName").asString()).isEqualTo("OTHER");
		assertThat(body.path("workStartDate").asString()).isEqualTo("2020-01-01");
		assertThat(body.path("applicationDeadlineDate").asString()).isEqualTo("2019-12-31");
		assertThat(jdbc.queryForObject("select recruitment_count from job_posting where id = ?", Integer.class,
				body.path("jobPostingId").asLong())).isEqualTo(32767);
		assertThat(jdbc.queryForObject("select count(*) from job_posting", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from job_posting_work_time", Integer.class)).isZero();
		assertThat(mapper.readTree(send("GET", "/api/employers/me/job-postings", accessToken, null).body()).path("jobPostings").size()).isZero();
		assertThat(mapper.readTree(send("GET", "/api/employers/me/job-postings?includeDraft=true", accessToken, null).body()).path("jobPostings").size()).isEqualTo(3);
	}

	@Test
	void requiresAllRecruitingFieldsAndRejectsPastDatesButAllowsEitherDateOrder() throws Exception {
		long owner = createUser("EMPLOYER");
		long workplace = createWorkplace(owner, "테스트 사업장");
		String accessToken = token(owner, "EMPLOYER");
		Map<String, Object> complete = recruitingRequest(workplace);
		for (String field : complete.keySet()) {
			Map<String, Object> missing = new LinkedHashMap<>(complete);
			missing.remove(field);
			assertError(create(accessToken, missing), 400, "INVALID_INPUT");
			missing.put(field, null);
			assertError(create(accessToken, missing), 400, "INVALID_INPUT");
		}
		Map<String, Object> request = new LinkedHashMap<>(complete);
		request.put("workTimes", List.of());
		assertError(create(accessToken, request), 400, "INVALID_INPUT");
		for (String field : List.of("workStartDate", "applicationDeadlineDate")) {
			request = new LinkedHashMap<>(complete);
			request.put(field, LocalDate.now(KOREA_ZONE).minusDays(1).toString());
			assertError(create(accessToken, request), 400, "INVALID_INPUT");
		}
		assertNoPostings();
		request = new LinkedHashMap<>(complete);
		request.put("workStartDate", LocalDate.now(KOREA_ZONE).plusDays(10).toString());
		request.put("applicationDeadlineDate", LocalDate.now(KOREA_ZONE).toString());
		HttpResponse<String> response = create(accessToken, request);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
	}

	@Test
	void validatesProvidedDraftValuesAndRejectsUnknownFieldsAndWrongTypes() throws Exception {
		long owner = createUser("EMPLOYER");
		long workplace = createWorkplace(owner, "테스트 사업장");
		String accessToken = token(owner, "EMPLOYER");
		List<Map<String, Object>> invalid = List.of(
				Map.of("workplaceId", "1"), Map.of("workplaceId", 0), Map.of("workplaceId", 1.5),
				Map.of("businessTypeId", "1"), Map.of("businessTypeId", -1),
				Map.of("jobRole", "  "), Map.of("jobRole", 123), Map.of("jobRole", "가".repeat(101)),
				Map.of("recruitmentCount", 0), Map.of("recruitmentCount", 32768), Map.of("recruitmentCount", 1.5),
				Map.of("wageAmount", 0), Map.of("wageAmount", "13000"), Map.of("wageAmount", 2147483648L),
				Map.of("wageType", "DAILY"), Map.of("requiredKoreanLevel", "TOPIK_3"), Map.of("preferredExperience", "UNKNOWN"),
				Map.of("workStartDate", "2026-02-30"), Map.of("workStartDate", "2026-1-01"), Map.of("workStartDate", "0000-01-01"),
				Map.of("applicationDeadlineDate", "2026-11-01T00:00:00"), Map.of("applicationDeadlineDate", 20261101),
				Map.of("workTimeNegotiable", "false"), Map.of("workTimeNegotiable", 1),
				Map.of("status", "CLOSED"), Map.of("status", "draft"), Map.of("status", true),
				Map.of("title", "직접 입력한 제목"), Map.of("otherJobName", "기타"), Map.of("employerUserId", owner));
		for (Map<String, Object> changes : invalid) {
			Map<String, Object> request = draftRequest(workplace);
			request.putAll(changes);
			assertError(create(accessToken, request), 400, "INVALID_INPUT");
		}
		for (String json : List.of("{}", "[]", "null")) {
			assertError(send("POST", "/api/job-postings", accessToken, json), 400, "INVALID_INPUT");
		}
		assertNoPostings();
	}

	@Test
	void rejectsMalformedOverlappingAndZeroLengthWorkTimesBeforeSaving() throws Exception {
		long owner = createUser("EMPLOYER");
		long workplace = createWorkplace(owner, "테스트 사업장");
		String accessToken = token(owner, "EMPLOYER");
		List<Object> invalid = List.of(
				"not-an-array", List.of("not-an-object"), List.of(Map.of("dayOfWeek", "MON")),
				List.of(Map.of("dayOfWeek", "MON", "startTime", "08:00", "endTime", "12:00", "id", 1)),
				List.of(time("mon", "08:00", "12:00")), List.of(time("MON", "8:00", "12:00")),
				List.of(time("MON", "08:15", "12:00")), List.of(time("MON", "08:00:00", "12:00")),
				List.of(time("MON", "22:00", "24:00")), List.of(time("MON", "22:00", "04:00")),
				List.of(time("MON", "08:00", "08:00")), List.of(time("MON", "00:00", "00:00")),
				List.of(time("MON", "08:00", "12:00"), time("MON", "08:00", "12:00")),
				List.of(time("MON", "10:00", "14:00"), time("MON", "08:00", "12:00")),
				List.of(time("MON", "22:00", "00:00"), time("MON", "23:00", "23:30")));
		for (Object workTimes : invalid) {
			Map<String, Object> request = draftRequest(workplace);
			request.put("workTimes", workTimes);
			assertError(create(accessToken, request), 400, "INVALID_INPUT");
		}
		assertError(send("POST", "/api/job-postings", accessToken,
				"{\"workplaceId\":" + workplace + ",\"status\":\"DRAFT\",\"workTimes\":[null]}"), 400, "INVALID_INPUT");
		assertNoPostings();
	}

	@Test
	void requiresActiveEmployerAndOwnedExistingWorkplaceAndExistingBusinessType() throws Exception {
		long owner = createUser("EMPLOYER");
		long other = createUser("EMPLOYER");
		long student = createUser("STUDENT");
		long workplace = createWorkplace(owner, "테스트 사업장");
		Map<String, Object> request = draftRequest(workplace);
		assertError(create(null, request), 401, "UNAUTHORIZED");
		assertError(create("invalid-token", request), 401, "UNAUTHORIZED");
		assertError(create(token(99999, "EMPLOYER"), request), 401, "UNAUTHORIZED");
		assertError(create(token(student, "STUDENT"), request), 403, "FORBIDDEN");
		assertError(create(token(student, "EMPLOYER"), request), 403, "FORBIDDEN");
		assertError(create(token(other, "EMPLOYER"), request), 403, "FORBIDDEN");
		String accessToken = token(owner, "EMPLOYER");
		assertError(create(accessToken, draftRequest(99999)), 404, "WORKPLACE_NOT_FOUND");
		request.put("businessTypeId", 99999);
		assertError(create(accessToken, request), 404, "BUSINESS_TYPE_NOT_FOUND");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", owner);
		assertError(create(accessToken, draftRequest(workplace)), 403, "WITHDRAWN_ACCOUNT");
		assertNoPostings();
	}

	@Test
	void rollsBackPostingAndEarlierWorkTimeWhenLaterWorkTimeSaveFails() throws Exception {
		long owner = createUser("EMPLOYER");
		long workplace = createWorkplace(owner, "테스트 사업장");
		String accessToken = token(owner, "EMPLOYER");
		Map<String, Object> existing = draftRequest(workplace);
		existing.put("workTimes", List.of(time("MON", "08:00", "12:00")));
		assertThat(create(accessToken, existing).statusCode()).isEqualTo(201);
		Map<String, List<Map<String, Object>>> before = snapshot();
		jdbc.execute("alter table job_posting_work_time add constraint test_reject_tuesday check (day_of_week <> 'TUE')");
		try {
			Map<String, Object> request = recruitingRequest(workplace);
			request.put("workTimes", List.of(time("MON", "22:00", "00:00"), time("TUE", "00:00", "04:00")));
			HttpResponse<String> response = create(accessToken, request);
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(snapshot()).isEqualTo(before);
		} finally {
			jdbc.execute("alter table job_posting_work_time drop constraint test_reject_tuesday");
		}
	}

	@Test
	void shortensOnlyGeneratedTitleWithoutSplittingUnicodeCharacters() throws Exception {
		long owner = createUser("EMPLOYER");
		String workplaceName = "상".repeat(150);
		String jobRole = "직".repeat(47) + "😀" + "무".repeat(51);
		long workplace = createWorkplace(owner, workplaceName);
		Map<String, Object> request = draftRequest(workplace);
		request.put("jobRole", jobRole);
		HttpResponse<String> response = create(token(owner, "EMPLOYER"), request);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		JsonNode body = mapper.readTree(response.body());
		String title = body.path("title").asString();
		assertThat(title.codePointCount(0, title.length())).isEqualTo(200);
		assertThat(title).endsWith("😀…");
		assertThat(body.path("jobRole").asString()).isEqualTo(jobRole);
		assertThat(body.path("workplaceName").asString()).isEqualTo(workplaceName);
		assertThat(jdbc.queryForObject("select char_length(title) from job_posting where id = ?", Integer.class,
				body.path("jobPostingId").asLong())).isEqualTo(200);
		assertThat(jdbc.queryForObject("select job_role from job_posting where id = ?", String.class,
				body.path("jobPostingId").asLong())).isEqualTo(jobRole);
	}

	private Map<String, Object> draftRequest(long workplaceId) {
		return new LinkedHashMap<>(Map.of("workplaceId", workplaceId, "status", "DRAFT"));
	}

	private Map<String, Object> recruitingRequest(long workplaceId) {
		Map<String, Object> request = draftRequest(workplaceId);
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
		request.put("workTimes", List.of(time("FRI", "16:00", "22:00"), time("MON", "12:00", "14:00"),
				time("MON", "22:00", "00:00"), time("MON", "08:00", "12:00"), time("TUE", "00:00", "04:00")));
		return request;
	}

	private Map<String, String> time(String day, String start, String end) {
		return Map.of("dayOfWeek", day, "startTime", start, "endTime", end);
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

	private HttpResponse<String> create(String token, Map<String, Object> request) throws Exception {
		return send("POST", "/api/job-postings", token, mapper.writeValueAsString(request));
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

	private void assertNoPostings() {
		assertThat(jdbc.queryForObject("select count(*) from job_posting", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from job_posting_work_time", Integer.class)).isZero();
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
