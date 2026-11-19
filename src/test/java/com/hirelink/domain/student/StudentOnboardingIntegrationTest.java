package com.hirelink.domain.student;

import com.hirelink.support.PostgreSQLIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentOnboardingIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String PATH = "/api/students/me/onboarding";
	private static final String JWT_SECRET = signingKey();
	private static final String FULL_REQUEST = """
			{
			  "birthDate":"2000-03-14", "currentRegion":"서울 마포구", "koreanLevel":"INTERMEDIATE",
			  "careers":[{"careerName":"카페", "startMonth":"2025-01", "endMonth":"2025-06", "description":"음료 제조"}],
			  "topikLevel":4,
			  "matchingPreferences":{
			    "businessTypeFlexible":false,
			    "preferredJobs":[{"businessTypeId":1},{"businessTypeId":8,"otherJobName":"영화관"}],
			    "preferredWorkTimes":[
			      {"dayOfWeek":"MON","startTime":"22:00","endTime":"00:00"},
			      {"dayOfWeek":"TUE","startTime":"00:00","endTime":"02:00"}],
			    "preferredLocation":{"locationName":"강남역","latitude":37.4979,"longitude":127.0276,"radiusM":750},
			    "priorities":[{"criterionId":1,"priorityOrder":1},{"criterionId":2,"priorityOrder":2},
			      {"criterionId":3,"priorityOrder":3},{"criterionId":4,"priorityOrder":4}]
			  }
			}
			""";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) { registry.add("jwt.secret", () -> JWT_SECRET); }

	@LocalServerPort private int port;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;
	@Autowired private JwtEncoder jwtEncoder;
	@Autowired private DataSource dataSource;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearDatabase() { jdbc.execute("TRUNCATE TABLE user_account RESTART IDENTITY CASCADE"); }

	@Test
	void completesAllDataAndKeepsVerifiedIdentityAndTimestamps() throws Exception {
		long user = verifiedStudent();
		Map<String, Object> verification = verification(user);
		HttpResponse<String> response = post(user, FULL_REQUEST);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode result = mapper.readTree(response.body());
		assertThat(result.size()).isEqualTo(3);
		assertThat(result.path("studentId").asLong()).isEqualTo(user);
		assertThat(result.path("completed").asBoolean()).isTrue();
		assertThat(Instant.parse(result.path("completedAt").asString())).isEqualTo(jdbc.queryForObject(
				"select onboarding_completed_at from student_profile where id = ?", java.sql.Timestamp.class, user).toInstant());
		Map<String, Object> profile = jdbc.queryForMap("select * from student_profile where id = ?", user);
		assertThat(profile).containsEntry("nationality", "VN").containsEntry("visa_code", "D-2")
				.containsEntry("current_region", "서울 마포구").containsEntry("korean_level", "INTERMEDIATE")
				.containsEntry("stay_expiration_date", verification.get("stay_expires_on"));
		assertThat(((Number) profile.get("topik_level")).intValue()).isEqualTo(4);
		assertThat(profile.get("birth_date").toString()).isEqualTo("2000-03-14");
		assertThat(verification(user)).isEqualTo(verification);
		assertThat(jdbc.queryForObject("select name from user_account where id = ?", String.class, user)).isEqualTo("인증 이름");
		assertThat(count("student_career", user)).isEqualTo(1);
		assertThat(count("student_preferred_job", user)).isEqualTo(2);
		assertThat(count("student_preferred_work_time", user)).isEqualTo(2);
		assertThat(count("student_priority", user)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select radius_m from student_preferred_location where user_id = ?", Integer.class, user)).isEqualTo(750);
		assertThat(jdbc.queryForObject("select minimum_hourly_wage from student_job_preference where user_id = ?", Integer.class, user)).isNull();
		HttpResponse<String> read = send("GET", "/api/students/me/profile", token(user, "STUDENT"), null);
		assertThat(read.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(read.body()).path("careers").size()).isEqualTo(1);
		assertThat(send("GET", "/api/students/me/matching-preferences", token(user, "STUDENT"), null).statusCode()).isEqualTo(200);
	}

	@Test
	void explicitlyAcceptsNoCareerNoTopikAndAnyBusinessType() throws Exception {
		long user = verifiedStudent();
		ObjectNode body = body();
		body.putArray("careers");
		body.putNull("topikLevel");
		preferences(body).put("businessTypeFlexible", true);
		preferences(body).putArray("preferredJobs");
		assertThat(post(user, body.toString()).statusCode()).isEqualTo(200);
		assertThat(count("student_career", user)).isZero();
		assertThat(count("student_preferred_job", user)).isZero();
		Map<String, Object> profile = jdbc.queryForMap("select topik_level, onboarding_completed_at from student_profile where id = ?", user);
		assertThat(profile.get("topik_level")).isNull();
		assertThat(profile.get("onboarding_completed_at")).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 1})
	void completionPreservesExpiredTodayAndFutureVerifiedStayDates(int daysFromToday) throws Exception {
		long user = verifiedStudent();
		LocalDate expiry = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(daysFromToday);
		jdbc.update("update student_registration_verification set stay_expires_on = ?, document_verified_at = card_verified_at where user_id = ?", expiry, user);
		Map<String, Object> before = verification(user);
		assertThat(post(user, FULL_REQUEST).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select stay_expiration_date from student_profile where id = ?", LocalDate.class, user)).isEqualTo(expiry);
		assertThat(verification(user)).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(strings = {"birthDate", "currentRegion", "koreanLevel", "careers", "topikLevel", "matchingPreferences"})
	void requiresEveryOnboardingSection(String field) throws Exception {
		long user = verifiedStudent();
		ObjectNode missing = body();
		missing.remove(field);
		assertError(post(user, missing.toString()), 400, "INVALID_INPUT");
		if (!field.equals("topikLevel")) {
			ObjectNode explicitNull = body();
			explicitNull.putNull(field);
			assertError(post(user, explicitNull.toString()), 400, "INVALID_INPUT");
		}
		assertNoOnboarding(user);
	}

	@ParameterizedTest
	@ValueSource(strings = {"businessTypeFlexible", "preferredJobs", "preferredWorkTimes", "preferredLocation", "priorities"})
	void requiresCompleteMatchingPreferences(String field) throws Exception {
		long user = verifiedStudent();
		ObjectNode request = body();
		preferences(request).remove(field);
		assertError(post(user, request.toString()), 400, "INVALID_INPUT");
		assertNoOnboarding(user);
	}

	@Test
	void rejectsEmptyTimesInvalidNightRangeOverlapAndIncompletePriorities() throws Exception {
		long user = verifiedStudent();
		for (String times : List.of("[]", "[{\"dayOfWeek\":\"MON\",\"startTime\":\"22:00\",\"endTime\":\"02:00\"}]",
				"[{\"dayOfWeek\":\"MON\",\"startTime\":\"22:15\",\"endTime\":\"00:00\"}]",
				"[{\"dayOfWeek\":\"MON\",\"startTime\":\"22:00\",\"endTime\":\"00:00\"},{\"dayOfWeek\":\"MON\",\"startTime\":\"23:00\",\"endTime\":\"00:00\"}]")) {
			ObjectNode request = body();
			preferences(request).set("preferredWorkTimes", mapper.readTree(times));
			assertError(post(user, request.toString()), 400, "INVALID_INPUT");
		}
		ObjectNode noPriority = body();
		preferences(noPriority).putArray("priorities");
		assertError(post(user, noPriority.toString()), 400, "INVALID_INPUT");
		ObjectNode duplicate = body();
		((ObjectNode) preferences(duplicate).path("priorities").get(1)).put("priorityOrder", 1);
		assertError(post(user, duplicate.toString()), 400, "INVALID_INPUT");
		assertNoOnboarding(user);
	}

	@Test
	void rejectsIncompleteOrInvalidCareersAndRollsBackEarlierCareerInsert() throws Exception {
		long user = verifiedStudent();
		for (String field : List.of("careerName", "startMonth", "endMonth", "description")) {
			ObjectNode request = body();
			((ObjectNode) request.path("careers").get(0)).remove(field);
			assertError(post(user, request.toString()), 400, "INVALID_INPUT");
		}
		ObjectNode request = body();
		var careers = request.withArray("careers");
		ObjectNode invalid = ((ObjectNode) careers.get(0)).deepCopy();
		invalid.put("endMonth", "2024-01");
		careers.add(invalid);
		assertError(post(user, request.toString()), 400, "INVALID_INPUT");
		assertNoOnboarding(user);
		ObjectNode blank = body();
		((ObjectNode) blank.path("careers").get(0)).put("description", "  ");
		assertError(post(user, blank.toString()), 400, "INVALID_INPUT");
	}

	@Test
	void rejectsInvalidScalarTypesAndAttemptsToSupplyVerifiedOrCompletionFields() throws Exception {
		long user = verifiedStudent();
		for (String field : List.of("userId", "name", "nationality", "visaCode", "stayExpirationDate", "registrationNumber", "completedAt", "topikLevelProvided")) {
			ObjectNode request = body();
			request.put(field, "forged");
			assertError(post(user, request.toString()), 400, "INVALID_INPUT");
		}
		ObjectNode bypass = body();
		bypass.remove("topikLevel");
		bypass.put("topikLevelProvided", true);
		assertError(post(user, bypass.toString()), 400, "INVALID_INPUT");
		for (String value : List.of("0", "7", "1.5", "\"4\"", "true")) {
			ObjectNode request = body();
			request.set("topikLevel", mapper.readTree(value));
			assertError(post(user, request.toString()), 400, "INVALID_INPUT");
		}
		for (String invalid : List.of("{}", "null", "[]", "{")) {
			assertError(post(user, invalid), 400, "INVALID_INPUT");
		}
		assertNoOnboarding(user);
	}

	@Test
	void usesExistingAgeAndBusinessValidation() throws Exception {
		long user = verifiedStudent();
		ObjectNode underage = body();
		underage.put("birthDate", LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(18).plusDays(1).toString());
		assertError(post(user, underage.toString()), 400, "INVALID_INPUT");
		ObjectNode missingOtherName = body();
		((ObjectNode) preferences(missingOtherName).path("preferredJobs").get(1)).remove("otherJobName");
		assertError(post(user, missingOtherName.toString()), 400, "INVALID_INPUT");
		ObjectNode unknownBusiness = body();
		((ObjectNode) preferences(unknownBusiness).path("preferredJobs").get(0)).put("businessTypeId", 99999);
		assertError(post(user, unknownBusiness.toString()), 404, "MATCHING_REFERENCE_NOT_FOUND");
		assertNoOnboarding(user);
	}

	@Test
	void requiresAuthenticatedActiveStudentPhoneAndOwnRegistrationRecord() throws Exception {
		assertError(send("POST", PATH, null, FULL_REQUEST), 401, "UNAUTHORIZED");
		assertError(send("POST", PATH, "invalid", FULL_REQUEST), 401, "UNAUTHORIZED");
		assertError(post(99999, FULL_REQUEST), 401, "UNAUTHORIZED");
		long employer = user("EMPLOYER");
		assertError(send("POST", PATH, token(employer, "EMPLOYER"), FULL_REQUEST), 403, "FORBIDDEN");
		assertError(post(employer, FULL_REQUEST), 403, "FORBIDDEN");
		long noPhone = user("STUDENT");
		assertError(post(noPhone, FULL_REQUEST), 409, "PHONE_NOT_VERIFIED");
		long local = user("STUDENT");
		verifyPhone(local);
		verifiedStudent();
		assertError(post(local, FULL_REQUEST), 409, "REGISTRATION_VERIFICATION_REQUIRED");
		long withdrawn = verifiedStudent();
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", withdrawn);
		assertError(post(withdrawn, FULL_REQUEST), 403, "WITHDRAWN_ACCOUNT");
		assertNoOnboarding(local);
	}

	@Test
	void replacesIncompleteDataButPreservesUnrelatedProfileFieldsAndUnspecifiedWage() throws Exception {
		long user = verifiedStudent();
		seedIncomplete(user);
		ObjectNode request = body();
		request.putArray("careers");
		request.putNull("topikLevel");
		assertThat(post(user, request.toString()).statusCode()).isEqualTo(200);
		assertThat(count("student_career", user)).isZero();
		Map<String, Object> profile = jdbc.queryForMap("select * from student_profile where id = ?", user);
		assertThat(profile).containsEntry("university_name", "기존 학교").containsEntry("academic_state", "ENROLLED")
				.containsEntry("nationality", "VN").containsEntry("topik_level", null).containsEntry("current_region", "서울 마포구");
		assertThat(jdbc.queryForObject("select minimum_hourly_wage from student_job_preference where user_id = ?", Integer.class, user)).isEqualTo(12000);
		assertThat(count("student_priority", user)).isEqualTo(4);
	}

	@Test
	void rollsBackNewProfileAndEverySectionWhenFinalCompletionWriteFails() throws Exception {
		long user = verifiedStudent();
		Map<String, Object> verification = verification(user);
		jdbc.execute("alter table student_profile add constraint test_onboarding_failure check (onboarding_completed_at is null)");
		try {
			assertError(post(user, FULL_REQUEST), 500, "INTERNAL_SERVER_ERROR");
			assertNoOnboarding(user);
			assertThat(verification(user)).isEqualTo(verification);
		} finally {
			jdbc.execute("alter table student_profile drop constraint test_onboarding_failure");
		}
		assertThat(post(user, FULL_REQUEST).statusCode()).isEqualTo(200);
	}

	@Test
	void restoresExistingProfileCareersAndPreferencesWhenLaterSaveFails() throws Exception {
		long user = verifiedStudent();
		seedIncomplete(user);
		List<List<Map<String, Object>>> before = snapshot(user);
		jdbc.execute("alter table student_priority add constraint test_onboarding_priority_failure check (priority_order <> 4) not valid");
		try {
			assertError(post(user, FULL_REQUEST), 500, "INTERNAL_SERVER_ERROR");
			assertThat(snapshot(user)).isEqualTo(before);
		} finally {
			jdbc.execute("alter table student_priority drop constraint test_onboarding_priority_failure");
		}
	}

	@Test
	void repeatCompletionDoesNotOverwriteDataOrCompletionTime() throws Exception {
		long user = verifiedStudent();
		assertThat(post(user, FULL_REQUEST).statusCode()).isEqualTo(200);
		List<List<Map<String, Object>>> before = snapshot(user);
		assertError(post(user, FULL_REQUEST.replace("서울 마포구", "다른 지역")), 409, "ONBOARDING_ALREADY_COMPLETED");
		assertThat(snapshot(user)).isEqualTo(before);
		assertThat(count("student_career", user)).isEqualTo(1);
		Object completedAt = jdbc.queryForMap("select onboarding_completed_at from student_profile where id = ?", user).get("onboarding_completed_at");
		assertThat(send("PATCH", "/api/students/me/profile", token(user, "STUDENT"), "{\"currentRegion\":\"수정 지역\"}").statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForMap("select onboarding_completed_at from student_profile where id = ?", user).get("onboarding_completed_at")).isEqualTo(completedAt);
	}

	@Test
	void simultaneousCompletionRequestsSaveOnlyOneSet() throws Exception {
		long user = verifiedStudent();
		CompletableFuture<HttpResponse<String>> first;
		CompletableFuture<HttpResponse<String>> second;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from user_account where id = ? for update")) {
				statement.setLong(1, user);
				statement.executeQuery().close();
			}
			try {
				first = client.sendAsync(request("POST", PATH, token(user, "STUDENT"), FULL_REQUEST), HttpResponse.BodyHandlers.ofString());
				second = client.sendAsync(request("POST", PATH, token(user, "STUDENT"), FULL_REQUEST), HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%user_account%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally { connection.rollback(); }
		}
		assertThat(List.of(first.get(15, TimeUnit.SECONDS).statusCode(), second.get(15, TimeUnit.SECONDS).statusCode()))
				.containsExactlyInAnyOrder(200, 409);
		assertThat(count("student_career", user)).isEqualTo(1);
		assertThat(count("student_priority", user)).isEqualTo(4);
	}

	@Test
	void standaloneCareerWritesAlsoWaitForTheMemberLock() throws Exception {
		long user = verifiedStudent();
		seedIncomplete(user);
		CompletableFuture<HttpResponse<String>> pending;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from user_account where id = ? for update")) {
				statement.setLong(1, user);
				statement.executeQuery().close();
			}
			try {
				pending = client.sendAsync(request("POST", "/api/students/me/careers", token(user, "STUDENT"), body().path("careers").get(0).toString()), HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%user_account%'
						""", Integer.class)).isGreaterThanOrEqualTo(1));
				assertThat(pending.isDone()).isFalse();
			} finally { connection.rollback(); }
		}
		assertThat(pending.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
	}

	@Test
	void openApiDocumentsRequiredFieldsNullableTopikAndCompletionConflicts() throws Exception {
		HttpResponse<String> response = send("GET", "/v3/api-docs", null, null);
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode document = mapper.readTree(response.body());
		JsonNode operation = document.path("paths").path(PATH).path("post");
		assertThat(operation.path("responses").has("200")).isTrue();
		assertThat(operation.path("responses").has("409")).isTrue();
		JsonNode schema = document.path("components").path("schemas").path("StudentOnboardingRequest");
		assertThat(schema.path("required").toString()).contains("birthDate", "currentRegion", "koreanLevel", "careers", "topikLevel", "matchingPreferences");
		assertThat(schema.path("properties").has("topikLevelProvided")).isFalse();
		assertThat(schema.path("properties").has("topikAnswerPresent")).isFalse();
		assertThat(schema.path("properties").path("topikLevel").toString()).contains("null");
	}

	private void seedIncomplete(long user) {
		jdbc.update("""
				insert into student_profile(id,birth_date,nationality,current_region,korean_level,visa_code,university_name,academic_state,topik_level)
				values (?, '1999-01-01','CN','기존 지역','BEGINNER','D-2','기존 학교','ENROLLED',2)
				""", user);
		jdbc.update("insert into student_career(user_id,career_name,start_month,end_month,description) values (?, '기존 경력','2024-01','2024-02','기존 내용')", user);
		jdbc.update("insert into student_job_preference(user_id,business_type_flexible,minimum_hourly_wage) values (?,true,12000)", user);
	}

	private ObjectNode body() { return (ObjectNode) mapper.readTree(FULL_REQUEST); }
	private ObjectNode preferences(ObjectNode body) { return (ObjectNode) body.path("matchingPreferences"); }
	private Map<String, Object> verification(long user) { return jdbc.queryForMap("select * from student_registration_verification where user_id = ?", user); }
	private int count(String table, long user) { return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, user); }

	private void assertNoOnboarding(long user) {
		assertThat(jdbc.queryForObject("select count(*) from student_profile where id = ?", Integer.class, user)).isZero();
		for (String table : List.of("student_career", "student_job_preference", "student_preferred_job", "student_preferred_work_time", "student_preferred_location", "student_priority")) {
			assertThat(count(table, user)).as(table).isZero();
		}
	}

	private List<List<Map<String, Object>>> snapshot(long user) {
		return List.of("user_account", "student_profile", "student_career", "student_job_preference", "student_preferred_job", "student_preferred_work_time", "student_preferred_location", "student_priority", "student_registration_verification")
				.stream().map(table -> jdbc.queryForList("select * from " + table + " where "
						+ (table.equals("student_profile") || table.equals("user_account") ? "id" : "user_id") + " = ? order by 1, 2", user)).toList();
	}

	private long user(String role) {
		return jdbc.queryForObject("insert into user_account(email,role,name) values (?,?,'인증 이름') returning id", Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private void verifyPhone(long user) {
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?", String.format(java.util.Locale.ROOT, "010%08d", user), user);
	}

	private long verifiedStudent() {
		long user = user("STUDENT");
		verifyPhone(user);
		jdbc.update("""
				insert into student_registration_verification(user_id,nationality,visa_code,stay_expires_on,card_verified_at,registered_at)
				values (?,'VN','D-2',?,now() - interval '1 day',now() - interval '1 day')
				""", user, LocalDate.now(ZoneId.of("Asia/Seoul")).plusMonths(3));
		return user;
	}

	private String token(long user, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(user))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> post(long user, String body) throws Exception { return send("POST", PATH, token(user, "STUDENT"), body); }
	private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
		return client.send(request(method, path, token, body), HttpResponse.BodyHandlers.ofString());
	}
	private HttpRequest request(String method, String path, String token, String body) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (token != null) { request.header("Authorization", "Bearer " + token); }
		return request.build();
	}
	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode result = mapper.readTree(response.body());
		assertThat(result.path("code").asString()).isEqualTo(code);
		assertThat(result.size()).isEqualTo(2);
	}
	private static String signingKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
