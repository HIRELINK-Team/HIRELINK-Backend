package com.hirelink.domain.student;

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
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentMatchingPreferenceReadIntegrationTest {

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
	private ObjectMapper mapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting, workplace, student_career, student_profile, user_term_agreement, "
				+ "phone_verification, phone_verification_send_attempt, refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void registersReferenceIdsAndAdvancesIdentitySequences() {
		assertThat(jdbc.queryForList("select name from business_type order by id", String.class))
				.containsExactly("FOOD_CAFE", "RETAIL", "OFFICE_EVENT_TOURISM", "INTERPRETATION_TRANSLATION",
						"DUTY_FREE", "MAJOR_RELATED", "INTERNSHIP", "OTHER");
		assertThat(jdbc.queryForList("select id from business_type order by id", Long.class))
				.containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
		assertThat(jdbc.queryForList("select criterion_name from priority_criterion order by id", String.class))
				.containsExactly("WORK_TIME", "WAGE", "DISTANCE", "BUSINESS_TYPE");
		assertThat(jdbc.queryForList("select id from priority_criterion order by id", Long.class))
				.containsExactly(1L, 2L, 3L, 4L);
		try {
			assertThat(jdbc.queryForObject("insert into business_type(name) values ('TEST_BUSINESS') returning id", Long.class))
					.isGreaterThan(8L);
			assertThat(jdbc.queryForObject("insert into priority_criterion(criterion_name) values ('TEST_CRITERION') returning id", Long.class))
					.isGreaterThan(4L);
		} finally {
			jdbc.update("delete from business_type where name = 'TEST_BUSINESS'");
			jdbc.update("delete from priority_criterion where criterion_name = 'TEST_CRITERION'");
		}
	}

	@Test
	void returnsEmptyOrPartiallyEnteredPreferencesWithoutCreatingData() throws Exception {
		long userId = createUser("STUDENT", true);
		String token = token(userId, "STUDENT");
		Map<String, Object> profileBefore = jdbc.queryForMap("select * from student_profile where id = ?", userId);
		for (int i = 0; i < 2; i++) {
			HttpResponse<String> response = get(PATH, token);
			assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
			assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
			assertThat(mapper.readTree(response.body())).isEqualTo(mapper.readTree("""
					{"businessTypeFlexible":false,"preferredJobs":[],"preferredWorkTimes":[],
					 "preferredLocation":null,"minimumHourlyWage":null,"priorities":[]}
					"""));
		}
		for (String table : List.of("student_preferred_job", "student_preferred_work_time", "student_preferred_location",
				"student_job_preference", "student_priority")) {
			assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class)).isZero();
		}
		jdbc.update("insert into student_preferred_work_time(user_id, day_of_week, start_time, end_time) values (?, 'FRI', '09:30', '12:00')", userId);
		HttpResponse<String> partial = get(PATH, token);
		assertThat(partial.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(partial.body())).isEqualTo(mapper.readTree("""
				{"businessTypeFlexible":false,"preferredJobs":[],
				 "preferredWorkTimes":[{"dayOfWeek":"FRI","startTime":"09:30","endTime":"12:00"}],
				 "preferredLocation":null,"minimumHourlyWage":null,"priorities":[]}
				"""));
		assertThat(jdbc.queryForObject("select count(*) from student_job_preference", Integer.class)).isZero();
		assertThat(jdbc.queryForMap("select * from student_profile where id = ?", userId)).isEqualTo(profileBefore);
	}

	@Test
	void returnsOnlyCurrentStudentsSavedPreferencesInDisplayOrder() throws Exception {
		long userId = createUser("STUDENT", true);
		long otherUserId = createUser("STUDENT", true);
		jdbc.update("insert into student_job_preference values (?, 12000, false), (?, 20000, false)", userId, otherUserId);
		jdbc.update("""
				insert into student_preferred_job(user_id, business_type_id, other_job_name)
				values (?, 8, '영화관'), (?, 5, null), (?, 1, null), (?, 2, null)
				""", userId, userId, userId, otherUserId);
		jdbc.update("""
				insert into student_preferred_work_time(user_id, day_of_week, start_time, end_time)
				values (?, 'SUN', '10:00', '12:00'), (?, 'MON', '19:00', '22:00'),
				       (?, 'WED', '18:30', '22:00'), (?, 'MON', '13:00', '15:00'), (?, 'TUE', '08:00', '10:00')
				""", userId, userId, userId, userId, otherUserId);
		jdbc.update("""
				insert into student_preferred_location(user_id, location_name, latitude, longitude, radius_m)
				values (?, '강남역', 37.4979123, 127.0276123, 3000), (?, '다른 지역', 35.1234567, 129.1234567, 1000)
				""", userId, otherUserId);
		jdbc.update("""
				insert into student_priority(user_id, criterion_id, priority_order, weight)
				values (?, 1, 3, 0.2), (?, 2, 1, 0.4), (?, 3, 4, 0.1), (?, 4, 2, 0.3), (?, 3, 1, 1.0)
				""", userId, userId, userId, userId, otherUserId);
		List<Map<String, Object>> prioritiesBefore = jdbc.queryForList("select * from student_priority order by user_id, criterion_id");

		HttpResponse<String> response = get(PATH + "?userId=" + otherUserId, token(userId, "STUDENT"));
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(mapper.readTree(response.body())).isEqualTo(mapper.readTree("""
				{
				  "businessTypeFlexible": false,
				  "preferredJobs": [
				    {"businessTypeId":1,"businessTypeName":"FOOD_CAFE","otherJobName":null},
				    {"businessTypeId":5,"businessTypeName":"DUTY_FREE","otherJobName":null},
				    {"businessTypeId":8,"businessTypeName":"OTHER","otherJobName":"영화관"}
				  ],
				  "preferredWorkTimes": [
				    {"dayOfWeek":"MON","startTime":"13:00","endTime":"15:00"},
				    {"dayOfWeek":"MON","startTime":"19:00","endTime":"22:00"},
				    {"dayOfWeek":"WED","startTime":"18:30","endTime":"22:00"},
				    {"dayOfWeek":"SUN","startTime":"10:00","endTime":"12:00"}
				  ],
				  "preferredLocation": {"locationName":"강남역","latitude":37.4979123,"longitude":127.0276123,"radiusM":3000},
				  "minimumHourlyWage":12000,
				  "priorities": [
				    {"criterionId":2,"criterionName":"WAGE","priorityOrder":1},
				    {"criterionId":4,"criterionName":"BUSINESS_TYPE","priorityOrder":2},
				    {"criterionId":1,"criterionName":"WORK_TIME","priorityOrder":3},
				    {"criterionId":3,"criterionName":"DISTANCE","priorityOrder":4}
				  ]
				}
				"""));
		assertThat(jdbc.queryForList("select * from student_priority order by user_id, criterion_id")).isEqualTo(prioritiesBefore);
	}

	@Test
	void returnsNoSelectedJobsWhenBusinessTypeIsFlexibleWithoutChangingStoredRows() throws Exception {
		long userId = createUser("STUDENT", true);
		jdbc.update("insert into student_job_preference values (?, null, true)", userId);
		jdbc.update("insert into student_preferred_job(user_id, business_type_id) values (?, 1)", userId);
		HttpResponse<String> response = get(PATH, token(userId, "STUDENT"));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(mapper.readTree(response.body())).isEqualTo(mapper.readTree("""
				{"businessTypeFlexible":true,"preferredJobs":[],"preferredWorkTimes":[],
				 "preferredLocation":null,"minimumHourlyWage":null,"priorities":[]}
				"""));
		assertThat(jdbc.queryForObject("select count(*) from student_preferred_job where user_id = ?", Integer.class, userId)).isEqualTo(1);
	}

	@Test
	void requiresAuthenticationActiveStudentAndExistingProfile() throws Exception {
		assertError(get(PATH, null), 401, "UNAUTHORIZED");
		assertError(get(PATH, "invalid-token"), 401, "UNAUTHORIZED");
		assertError(get(PATH, token(99999, "STUDENT")), 401, "UNAUTHORIZED");
		long employerId = createUser("EMPLOYER", false);
		assertError(get(PATH, token(employerId, "EMPLOYER")), 403, "FORBIDDEN");
		assertError(get(PATH, token(employerId, "STUDENT")), 403, "FORBIDDEN");
		long userId = createUser("STUDENT", false);
		long otherUserId = createUser("STUDENT", true);
		assertError(get(PATH + "?userId=" + otherUserId, token(userId, "STUDENT")), 404, "STUDENT_PROFILE_NOT_FOUND");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", otherUserId);
		assertError(get(PATH, token(otherUserId, "STUDENT")), 403, "WITHDRAWN_ACCOUNT");
	}

	@Test
	void keepsErdKeysAndAllowsUnassignedWeightInDatabase() {
		long userId = createUser("STUDENT", true);
		assertThatThrownBy(() -> jdbc.update("insert into student_preferred_job(user_id, business_type_id) values (-1, 1)"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("insert into student_preferred_job(user_id, business_type_id) values (?, -1)", userId))
				.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("""
				insert into student_preferred_location(user_id, location_name, latitude, longitude, radius_m)
				values (?, '테스트 지역', 37, 127, 1000)
				""", userId);
		assertThatThrownBy(() -> jdbc.update("""
				insert into student_preferred_location(user_id, location_name, latitude, longitude, radius_m)
				values (?, '두 번째 지역', 37, 127, 2000)
				""", userId)).isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("insert into student_priority values (?, 1, 1, 0.4), (?, 2, 2, 0.3)", userId, userId);
		assertThatThrownBy(() -> jdbc.update("insert into student_priority values (?, 1, 3, 0.2)", userId))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("insert into student_priority values (?, 3, 1, 0.2)", userId))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("insert into student_priority values (?, -1, 3, 0.2)", userId))
				.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("insert into student_priority(user_id, criterion_id, priority_order) values (?, 3, 3)", userId);
		assertThat(jdbc.queryForMap("select weight from student_priority where user_id = ? and criterion_id = 3", userId))
				.containsEntry("weight", null);
	}

	@Test
	void returnsSafeErrorWhenDatabaseReadFails() throws Exception {
		long userId = createUser("STUDENT", true);
		jdbc.execute("alter table student_job_preference rename to test_unavailable_preference");
		try {
			HttpResponse<String> response = get(PATH, token(userId, "STUDENT"));
			assertError(response, 500, "INTERNAL_SERVER_ERROR");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("서버 오류가 발생했습니다.");
		} finally {
			jdbc.execute("alter table test_unavailable_preference rename to student_job_preference");
		}
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
		return client.send(request("GET", path, token), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String method, String path, String token) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(15)).method(method, HttpRequest.BodyPublishers.noBody());
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
