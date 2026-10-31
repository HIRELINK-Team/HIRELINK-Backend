package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

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
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentMatchingPreferenceUpdateIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final String PATH = "/api/students/me/matching-preferences";
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
	private DataSource dataSource;
	@Autowired
	private ObjectMapper mapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, "
				+ "phone_verification, phone_verification_send_attempt, refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	private static final String FULL_REQUEST = """
			{
			  "businessTypeFlexible":false,
			  "preferredJobs":[{"businessTypeId":8,"otherJobName":"영화관"},{"businessTypeId":1}],
			  "preferredWorkTimes":[
			    {"dayOfWeek":"SUN","startTime":"22:00","endTime":"00:00"},
			    {"dayOfWeek":"MON","startTime":"00:00","endTime":"02:00"},
			    {"dayOfWeek":"MON","startTime":"13:00","endTime":"15:00"}
			  ],
			  "preferredLocation":{"locationName":"강남역","latitude":37.4979123,"longitude":127.0276123,"radiusM":3000},
			  "minimumHourlyWage":12000,
			  "priorities":[
			    {"criterionId":1,"priorityOrder":1},{"criterionId":2,"priorityOrder":2},
			    {"criterionId":3,"priorityOrder":3},{"criterionId":4,"priorityOrder":4}
			  ]
			}
			""";

	@Test
	void createsOwnPreferencesAndReturnsTheSameDataAsGet() throws Exception {
		long userId = createUser("STUDENT", true);
		long otherId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		Map<String, Object> profileBefore = jdbc.queryForMap("select * from student_profile where id = ?", userId);
		HttpResponse<String> response = client.send(request("PATCH", PATH + "?userId=" + otherId, token, FULL_REQUEST),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(6);
		assertThat(body.path("preferredJobs")).isEqualTo(mapper.readTree("""
				[{"businessTypeId":1,"businessTypeName":"FOOD_CAFE","otherJobName":null},
				 {"businessTypeId":8,"businessTypeName":"OTHER","otherJobName":"영화관"}]
				"""));
		assertThat(body.path("preferredWorkTimes")).isEqualTo(mapper.readTree("""
				[{"dayOfWeek":"MON","startTime":"00:00","endTime":"02:00"},
				 {"dayOfWeek":"MON","startTime":"13:00","endTime":"15:00"},
				 {"dayOfWeek":"SUN","startTime":"22:00","endTime":"00:00"}]
				"""));
		assertThat(body.path("preferredLocation")).isEqualTo(mapper.readTree(FULL_REQUEST).path("preferredLocation"));
		assertThat(body.path("minimumHourlyWage").asInt()).isEqualTo(12000);
		assertThat(body.path("priorities").size()).isEqualTo(4);
		assertThat(body.path("priorities").get(0).path("criterionName").asString()).isEqualTo("WORK_TIME");
		assertThat(body.path("priorities").get(0).has("weight")).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from student_priority where user_id = ? and weight is null", Integer.class, userId)).isEqualTo(4);
		assertThat(mapper.readTree(get(PATH, token).body())).isEqualTo(body);
		assertThat(mapper.readTree(get(PATH, token(otherId, "STUDENT")).body()).path("preferredJobs").isEmpty()).isTrue();
		assertThat(snapshot(otherId)).allSatisfy(rows -> assertThat(rows).isEmpty());
		assertThat(jdbc.queryForMap("select * from student_profile where id = ?", userId)).isEqualTo(profileBefore);
	}

	@Test
	void updatesOnlyProvidedFieldsAndClearsOnlySupportedValues() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		assertThat(patch(FULL_REQUEST, token).statusCode()).isEqualTo(200);
		List<List<Map<String, Object>>> before = snapshot(userId);
		HttpResponse<String> wage = patch("{\"minimumHourlyWage\":13000}", token);
		assertThat(wage.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(wage.body()).path("minimumHourlyWage").asInt()).isEqualTo(13000);
		assertThat(snapshot(userId).subList(0, 4)).isEqualTo(before.subList(0, 4));
		assertThat(patch("{\"minimumHourlyWage\":null,\"preferredWorkTimes\":[]}", token).statusCode()).isEqualTo(200);
		JsonNode cleared = mapper.readTree(get(PATH, token).body());
		assertThat(cleared.path("minimumHourlyWage").isNull()).isTrue();
		assertThat(cleared.path("preferredWorkTimes").isEmpty()).isTrue();
		assertThat(cleared.path("preferredJobs").size()).isEqualTo(2);
		assertThat(cleared.path("preferredLocation").path("locationName").asString()).isEqualTo("강남역");
		assertThat(patch("{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":5}]}", token).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForList("select business_type_id from student_preferred_job where user_id = ?", Long.class, userId)).containsExactly(5L);
		assertThat(patch("{\"businessTypeFlexible\":true,\"preferredJobs\":[]}", token).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from student_preferred_job where user_id = ?", Integer.class, userId)).isZero();
		JsonNode flexible = mapper.readTree(get(PATH, token).body());
		assertThat(flexible.path("businessTypeFlexible").asBoolean()).isTrue();
		assertThat(flexible.path("preferredJobs").isEmpty()).isTrue();
		assertThat(patch("""
				{"preferredLocation":{"locationName":"새 지역","latitude":-90,"longitude":180,"radiusM":1}}
				""", token).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from student_preferred_location where user_id = ?", Integer.class, userId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select location_name from student_preferred_location where user_id = ?", String.class, userId)).isEqualTo("새 지역");
		assertThat(snapshot(userId).get(3)).isEqualTo(before.get(3));
	}

	@Test
	void preservesUnchangedWeightsAndClearsWeightsOnlyForChangedRanks() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		assertThat(patch(FULL_REQUEST, token).statusCode()).isEqualTo(200);
		jdbc.update("update student_priority set weight = criterion_id * 0.1 where user_id = ?", userId);
		String priorities = "{\"priorities\":" + mapper.readTree(FULL_REQUEST).path("priorities") + "}";
		List<Map<String, Object>> before = snapshot(userId).get(3);
		assertThat(patch(priorities, token).statusCode()).isEqualTo(200);
		assertThat(snapshot(userId).get(3)).isEqualTo(before);
		String swapped = """
				{"priorities":[{"criterionId":1,"priorityOrder":2},{"criterionId":2,"priorityOrder":1},
				 {"criterionId":3,"priorityOrder":3},{"criterionId":4,"priorityOrder":4}]}
				""";
		HttpResponse<String> response = patch(swapped, token);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(mapper.readTree(response.body()).path("priorities").get(0).path("criterionId").asLong()).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from student_priority where user_id = ? and weight is null", Integer.class, userId)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select weight from student_priority where user_id = ? and criterion_id = 3", java.math.BigDecimal.class, userId))
				.isEqualByComparingTo("0.3");
		assertThat(jdbc.queryForObject("select weight from student_priority where user_id = ? and criterion_id = 4", java.math.BigDecimal.class, userId))
				.isEqualByComparingTo("0.4");
		List<Map<String, Object>> swappedBefore = snapshot(userId).get(3);
		assertThat(patch(swapped, token).statusCode()).isEqualTo(200);
		assertThat(snapshot(userId).get(3)).isEqualTo(swappedBefore);
	}

	@Test
	void rejectsInconsistentJobSelectionsAndUnknownBusinessTypes() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		assertThat(patch(FULL_REQUEST, token).statusCode()).isEqualTo(200);
		List<List<Map<String, Object>>> before = snapshot(userId);
		for (String body : List.of(
				"{\"businessTypeFlexible\":true}",
				"{\"preferredJobs\":[{\"businessTypeId\":1}]}",
				"{\"businessTypeFlexible\":false,\"preferredJobs\":[]}",
				"{\"businessTypeFlexible\":true,\"preferredJobs\":[{\"businessTypeId\":1}]}",
				"{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":1},{\"businessTypeId\":1}]}",
				"{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":8}]}",
				"{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":8,\"otherJobName\":\"  \"}]}",
				"{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":1,\"otherJobName\":\"기타\"}]}",
				"{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":8,\"otherJobName\":\"" + "가".repeat(101) + "\"}]}")) {
			assertError(patch(body, token), 400, "INVALID_INPUT");
		}
		assertError(patch("{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":99999}]}", token), 404, "MATCHING_REFERENCE_NOT_FOUND");
		assertThat(snapshot(userId)).isEqualTo(before);
	}

	@Test
	void validatesHalfHourIntervalsOverlapsAndMidnightBoundaries() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		String adjacent = """
				{"preferredWorkTimes":[
				 {"dayOfWeek":"MON","startTime":"13:00","endTime":"15:00"},
				 {"dayOfWeek":"MON","startTime":"15:00","endTime":"16:30"},
				 {"dayOfWeek":"MON","startTime":"23:30","endTime":"00:00"},
				 {"dayOfWeek":"TUE","startTime":"00:00","endTime":"00:30"}]}
				""";
		assertThat(patch(adjacent, token).statusCode()).isEqualTo(200);
		List<List<Map<String, Object>>> before = snapshot(userId);
		for (String item : List.of(
				"{\"dayOfWeek\":\"MON\",\"startTime\":\"22:00\",\"endTime\":\"02:00\"}",
				"{\"dayOfWeek\":\"MON\",\"startTime\":\"00:00\",\"endTime\":\"00:00\"}",
				"{\"dayOfWeek\":\"MON\",\"startTime\":\"13:00\",\"endTime\":\"13:00\"}",
				"{\"dayOfWeek\":\"MON\",\"startTime\":\"9:00\",\"endTime\":\"12:00\"}",
				"{\"dayOfWeek\":\"MON\",\"startTime\":\"09:15\",\"endTime\":\"12:00\"}",
				"{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00:00\",\"endTime\":\"12:00\"}",
				"{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"24:00\"}",
				"{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}")) {
			assertError(patch("{\"preferredWorkTimes\":[" + item + "]}", token), 400, "INVALID_INPUT");
		}
		assertError(patch(adjacent.replace("\"15:00\",\"endTime\":\"16:30\"", "\"14:30\",\"endTime\":\"16:30\""), token), 400, "INVALID_INPUT");
		assertError(patch("""
				{"preferredWorkTimes":[{"dayOfWeek":"MON","startTime":"22:00","endTime":"00:00"},
				 {"dayOfWeek":"MON","startTime":"23:00","endTime":"23:30"}]}
				""", token), 400, "INVALID_INPUT");
		assertThat(snapshot(userId)).isEqualTo(before);
	}

	@Test
	void validatesLocationAndWageWithoutChangingExistingValues() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		assertThat(patch(FULL_REQUEST, token).statusCode()).isEqualTo(200);
		List<List<Map<String, Object>>> before = snapshot(userId);
		for (String location : List.of(
				"{\"locationName\":\" \" ,\"latitude\":37,\"longitude\":127,\"radiusM\":3000}",
				"{\"locationName\":\"지역\",\"latitude\":90.1,\"longitude\":127,\"radiusM\":3000}",
				"{\"locationName\":\"지역\",\"latitude\":37,\"longitude\":-180.1,\"radiusM\":3000}",
				"{\"locationName\":\"지역\",\"latitude\":37.12345678,\"longitude\":127,\"radiusM\":3000}",
				"{\"locationName\":\"지역\",\"latitude\":37,\"longitude\":127,\"radiusM\":0}",
				"{\"locationName\":\"지역\",\"latitude\":37,\"longitude\":127}",
				"{\"locationName\":\"" + "가".repeat(151) + "\",\"latitude\":37,\"longitude\":127,\"radiusM\":3000}")) {
			assertError(patch("{\"preferredLocation\":" + location + "}", token), 400, "INVALID_INPUT");
		}
		for (String wage : List.of("0", "-1", "1.5", "\"12000\"", "2147483648", "true")) {
			assertError(patch("{\"minimumHourlyWage\":" + wage + "}", token), 400, "INVALID_INPUT");
		}
		assertThat(snapshot(userId)).isEqualTo(before);
	}

	@Test
	void rejectsMissingDuplicateAndUnknownPriorityCriteria() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		assertThat(patch(FULL_REQUEST, token).statusCode()).isEqualTo(200);
		List<List<Map<String, Object>>> before = snapshot(userId);
		String valid = mapper.readTree(FULL_REQUEST).path("priorities").toString();
		for (String priorities : List.of("[]", "[{\"criterionId\":1,\"priorityOrder\":1}]",
				valid.replace("\"criterionId\":2", "\"criterionId\":1"),
				valid.replace("\"priorityOrder\":2", "\"priorityOrder\":1"),
				valid.replace("\"priorityOrder\":1", "\"priorityOrder\":0"),
				valid.replace("\"priorityOrder\":4", "\"priorityOrder\":5"))) {
			assertError(patch("{\"priorities\":" + priorities + "}", token), 400, "INVALID_INPUT");
		}
		assertError(patch("{\"priorities\":" + valid.replace("\"criterionId\":4", "\"criterionId\":99999") + "}", token),
				404, "MATCHING_REFERENCE_NOT_FOUND");
		assertThat(snapshot(userId)).isEqualTo(before);
	}

	@Test
	void rejectsEmptyUnknownNullAndIncorrectlyTypedInputs() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		for (String body : List.of("{}", "null", "[]", "{", "{\"userId\":1}",
				"{\"businessTypeFlexible\":\"true\",\"preferredJobs\":[]}",
				"{\"preferredJobs\":{}}", "{\"preferredJobs\":[null]}",
				"{\"preferredJobs\":[{\"businessTypeId\":1,\"extra\":true}]}",
				"{\"preferredJobs\":[{\"businessTypeId\":\"1\"}]}",
				"{\"preferredJobs\":[{\"businessTypeId\":9223372036854775808}]}",
				"{\"preferredWorkTimes\":[{}]}", "{\"preferredLocation\":{}}",
				"{\"priorities\":[{\"criterionId\":1,\"priorityOrder\":1,\"weight\":0.5}]}")) {
			assertError(patch(body, token), 400, "INVALID_INPUT");
		}
		for (String field : List.of("businessTypeFlexible", "preferredJobs", "preferredWorkTimes", "preferredLocation", "priorities")) {
			assertError(patch("{\"" + field + "\":null}", token), 400, "INVALID_INPUT");
		}
		assertThat(snapshot(userId)).allSatisfy(rows -> assertThat(rows).isEmpty());
	}

	@Test
	void requiresAuthenticationActiveStudentAndOwnProfile() throws Exception {
		assertError(patch(FULL_REQUEST, null), 401, "UNAUTHORIZED");
		assertError(patch(FULL_REQUEST, "invalid-token"), 401, "UNAUTHORIZED");
		assertError(patch(FULL_REQUEST, token(99999, "STUDENT")), 401, "UNAUTHORIZED");
		long employer = createUser("EMPLOYER", false);
		assertError(patch(FULL_REQUEST, token(employer, "EMPLOYER")), 403, "FORBIDDEN");
		assertError(patch(FULL_REQUEST, token(employer, "STUDENT")), 403, "FORBIDDEN");
		long noProfile = createUser("STUDENT", false);
		assertError(patch(FULL_REQUEST, token(noProfile, "STUDENT")), 404, "STUDENT_PROFILE_NOT_FOUND");
		long withdrawn = createUser("STUDENT", true);
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", withdrawn);
		assertError(patch(FULL_REQUEST, token(withdrawn, "STUDENT")), 403, "WITHDRAWN_ACCOUNT");
		assertThat(snapshot(withdrawn)).allSatisfy(rows -> assertThat(rows).isEmpty());
	}

	@Test
	void rollsBackAllChangesWhenPriorityInsertFails() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		assertThat(patch(FULL_REQUEST, token).statusCode()).isEqualTo(200);
		List<List<Map<String, Object>>> before = snapshot(userId);
		jdbc.execute("alter table student_priority add constraint test_priority_write_failure check (priority_order <> 4) not valid");
		try {
			HttpResponse<String> response = patch(FULL_REQUEST.replace("12000", "14000").replace("강남역", "새 지역")
					.replace("영화관", "새 업종명").replace("22:00", "23:00"), token);
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
			assertThat(snapshot(userId)).isEqualTo(before);
		} finally {
			jdbc.execute("alter table student_priority drop constraint test_priority_write_failure");
		}
	}

	@Test
	void serializesConcurrentFirstUpdatesWithoutLosingEitherField() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		CompletableFuture<HttpResponse<String>> first;
		CompletableFuture<HttpResponse<String>> second;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from user_account where id = ? for update")) {
				statement.setLong(1, userId);
				statement.executeQuery().close();
			}
			try {
				first = client.sendAsync(request("PATCH", PATH, token, "{\"minimumHourlyWage\":12000}"), HttpResponse.BodyHandlers.ofString());
				second = client.sendAsync(request("PATCH", PATH, token, "{\"businessTypeFlexible\":true,\"preferredJobs\":[]}"), HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%user_account%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally {
				connection.rollback();
			}
		}
		for (HttpResponse<String> response : List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))) {
			assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		}
		JsonNode body = mapper.readTree(get(PATH, token).body());
		assertThat(body.path("businessTypeFlexible").asBoolean()).isTrue();
		assertThat(body.path("minimumHourlyWage").asInt()).isEqualTo(12000);
		assertThat(jdbc.queryForObject("select count(*) from student_job_preference where user_id = ?", Integer.class, userId)).isEqualTo(1);
	}

	private List<List<Map<String, Object>>> snapshot(long userId) {
		return List.of("student_preferred_job", "student_preferred_work_time", "student_preferred_location", "student_priority", "student_job_preference")
				.stream().map(table -> jdbc.queryForList("select * from " + table + " where user_id = ? order by 1, 2", userId)).toList();
	}

	private HttpResponse<String> patch(String body, String token) throws Exception {
		return client.send(request("PATCH", PATH, token, body), HttpResponse.BodyHandlers.ofString());
	}

	private long createUser(String role, boolean withProfile) {
		long id = jdbc.queryForObject("insert into user_account(email, role, name) values (?, ?, '테스트 이름') returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
		if (withProfile) {
			jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
					String.format(java.util.Locale.ROOT, "010%08d", id), id);
			jdbc.update("""
					insert into student_profile(id, birth_date, nationality, current_region, korean_level)
					values (?, '2000-03-14', 'VN', '서울 마포구', 'INTERMEDIATE')
					""", id);
		}
		return id;
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> get(String path, String token) throws Exception {
		return client.send(request("GET", path, token, null), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String method, String path, String token, String body) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return request.build();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
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
