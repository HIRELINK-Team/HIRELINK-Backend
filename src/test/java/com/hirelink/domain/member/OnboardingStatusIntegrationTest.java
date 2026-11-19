package com.hirelink.domain.member;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OnboardingStatusIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String PATH = "/api/users/me/onboarding";
	private static final String JWT_SECRET = signingKey();
	private static final String COMPLETE_REQUEST = """
			{
			  "birthDate":"2000-05-14","currentRegion":"서울 마포구","koreanLevel":"INTERMEDIATE",
			  "careers":[],"topikLevel":null,
			  "matchingPreferences":{
			    "businessTypeFlexible":true,"preferredJobs":[],
			    "preferredWorkTimes":[{"dayOfWeek":"MON","startTime":"09:00","endTime":"13:00"}],
			    "preferredLocation":{"locationName":"서울 마포구","latitude":37.5665,"longitude":126.978,"radiusM":500},
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
	void restartsAtBasicInformationWithoutCreatingOrUpdatingData() throws Exception {
		long user = verifiedStudent(30);
		List<List<Map<String, Object>>> before = snapshot(user);
		assertStatus(get(user), user, "STUDENT", "REQUIRED", "BASIC_INFORMATION", null);
		assertStatus(get(user), user, "STUDENT", "REQUIRED", "BASIC_INFORMATION", null);
		assertThat(snapshot(user)).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from student_profile where id = ?", Integer.class, user)).isZero();
	}

	@Test
	void doesNotInferCompletionFromExistingProfileOrCareers() throws Exception {
		long user = verifiedStudent(30);
		createProfile(user);
		jdbc.update("insert into student_career(user_id,career_name,start_month,end_month,description) values (?,'기존 경력','2025-01','2025-02','근무 내용')", user);
		List<List<Map<String, Object>>> before = snapshot(user);
		assertStatus(get(user), user, "STUDENT", "REQUIRED", "BASIC_INFORMATION", null);
		assertThat(snapshot(user)).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 30})
	void readsCompletionAfterFinalSaveRegardlessOfStayExpiry(int expiryDays) throws Exception {
		long user = verifiedStudent(expiryDays);
		assertStatus(get(user), user, "STUDENT", "REQUIRED", "BASIC_INFORMATION", null);
		HttpResponse<String> completion = send("POST", "/api/students/me/onboarding", token(user, "STUDENT"), COMPLETE_REQUEST);
		assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
		Instant completedAt = Instant.parse(mapper.readTree(completion.body()).path("completedAt").asString());
		List<List<Map<String, Object>>> before = snapshot(user);
		assertStatus(get(user), user, "STUDENT", "COMPLETED", "NONE", completedAt);
		assertThat(snapshot(user)).isEqualTo(before);
		jdbc.update("update student_registration_verification set stay_expires_on = current_date - 1 where user_id = ?", user);
		jdbc.update("update student_profile set stay_expiration_date = current_date - 1 where id = ?", user);
		assertStatus(get(user), user, "STUDENT", "COMPLETED", "NONE", completedAt);
	}

	@ParameterizedTest
	@ValueSource(strings = {"LOCAL", "GOOGLE", "KAKAO"})
	void keepsUnverifiedLegacyStudentAccountsInTheirExistingFlow(String provider) throws Exception {
		long user = user("STUDENT", provider);
		verifiedStudent(30);
		assertStatus(get(user), user, "STUDENT", "NOT_APPLICABLE", "EXISTING_FLOW", null);
		createProfile(user);
		List<List<Map<String, Object>>> before = snapshot(user);
		assertStatus(get(user), user, "STUDENT", "NOT_APPLICABLE", "EXISTING_FLOW", null);
		assertThat(snapshot(user)).isEqualTo(before);
	}

	@Test
	void usesStoredRoleAndDoesNotApplyStudentOnboardingToEmployers() throws Exception {
		long user = user("EMPLOYER", "GOOGLE");
		assertStatus(send("GET", PATH, token(user, "EMPLOYER"), null), user, "EMPLOYER", "NOT_APPLICABLE", "EXISTING_FLOW", null);
		assertStatus(send("GET", PATH, token(user, "STUDENT"), null), user, "EMPLOYER", "NOT_APPLICABLE", "EXISTING_FLOW", null);
	}

	@Test
	void readsOnlyTheAuthenticatedMemberEvenWhenAnotherIdIsSupplied() throws Exception {
		long first = verifiedStudent(30);
		long other = verifiedStudent(30);
		assertThat(send("POST", "/api/students/me/onboarding", token(other, "STUDENT"), COMPLETE_REQUEST).statusCode()).isEqualTo(200);
		assertStatus(send("GET", PATH + "?userId=" + other, token(first, "STUDENT"), null), first, "STUDENT", "REQUIRED", "BASIC_INFORMATION", null);
	}

	@Test
	void excludesUncommittedCompletionAndShowsItAfterCommit() throws Exception {
		long user = verifiedStudent(30);
		createProfile(user);
		Instant completedAt = Instant.parse("2026-11-20T03:00:00Z");
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("update student_profile set onboarding_completed_at = ? where id = ?")) {
				statement.setTimestamp(1, java.sql.Timestamp.from(completedAt));
				statement.setLong(2, user);
				statement.executeUpdate();
			}
			assertStatus(get(user), user, "STUDENT", "REQUIRED", "BASIC_INFORMATION", null);
			connection.commit();
		}
		assertStatus(get(user), user, "STUDENT", "COMPLETED", "NONE", completedAt);
	}

	@Test
	void requiresAccountAccessTokenRatherThanSignupCookie() throws Exception {
		assertError(send("GET", PATH, null, null), 401, "UNAUTHORIZED");
		assertError(send("GET", PATH, "invalid-token", null), 401, "UNAUTHORIZED");
		assertError(get(99999), 401, "UNAUTHORIZED");
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
				.header("Cookie", "socialSignup=temporary-signup-session").GET().build();
		assertError(client.send(request, HttpResponse.BodyHandlers.ofString()), 401, "UNAUTHORIZED");
	}

	@ParameterizedTest
	@ValueSource(strings = {"STUDENT", "EMPLOYER"})
	void rejectsWithdrawnAccounts(String role) throws Exception {
		long user = user(role, "GOOGLE");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", user);
		assertError(send("GET", PATH, token(user, role), null), 403, "WITHDRAWN_ACCOUNT");
	}

	@Test
	void documentsAllStatusAndNavigationValuesWithoutChangingCompletionEndpoint() throws Exception {
		HttpResponse<String> response = send("GET", "/v3/api-docs", null, null);
		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode document = mapper.readTree(response.body());
		JsonNode operation = document.path("paths").path(PATH).path("get");
		assertThat(operation.path("responses").has("200")).isTrue();
		assertThat(operation.path("responses").has("401")).isTrue();
		assertThat(operation.path("responses").has("403")).isTrue();
		assertThat(operation.path("responses").path("200").path("content").path("application/json").path("examples").size()).isEqualTo(3);
		JsonNode schema = document.path("components").path("schemas").path("OnboardingStatusResponse");
		assertThat(schema.path("required").toString()).contains("userId", "role", "status", "nextStep", "completedAt");
		assertThat(schema.path("properties").path("status").toString()).contains("REQUIRED", "COMPLETED", "NOT_APPLICABLE");
		assertThat(schema.path("properties").path("nextStep").toString()).contains("BASIC_INFORMATION", "NONE", "EXISTING_FLOW");
		assertThat(schema.path("properties").path("completedAt").toString()).contains("null");
		assertThat(document.path("paths").path("/api/students/me/onboarding").has("post")).isTrue();
	}

	private long user(String role, String provider) {
		long user = jdbc.queryForObject("insert into user_account(email,role,name) values (?,?,'인증 이름') returning id", Long.class, UUID.randomUUID() + "@example.com", role);
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?", String.format(java.util.Locale.ROOT, "010%08d", user), user);
		jdbc.update("insert into auth_account(user_id,provider,provider_user_id) values (?,?,?)", user, provider, UUID.randomUUID().toString());
		return user;
	}

	private long verifiedStudent(int expiryDays) {
		long user = user("STUDENT", "GOOGLE");
		jdbc.update("""
				insert into student_registration_verification(user_id,nationality,visa_code,stay_expires_on,card_verified_at,registered_at)
				values (?,'VN','D-2',?,now(),now())
				""", user, LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(expiryDays));
		return user;
	}

	private void createProfile(long user) {
		jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level) values (?,'2000-05-14','VN','서울 마포구','INTERMEDIATE')", user);
	}

	private List<List<Map<String, Object>>> snapshot(long user) {
		return List.of("user_account", "student_profile", "student_registration_verification", "student_career")
				.stream().map(table -> jdbc.queryForList("select * from " + table + " where "
						+ (table.equals("user_account") || table.equals("student_profile") ? "id" : "user_id") + " = ? order by 1", user)).toList();
	}

	private HttpResponse<String> get(long user) throws Exception { return send("GET", PATH, token(user, "STUDENT"), null); }
	private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (token != null) { request.header("Authorization", "Bearer " + token); }
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private void assertStatus(HttpResponse<String> response, long user, String role, String status, String nextStep, Instant completedAt) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.size()).isEqualTo(5);
		assertThat(body.path("userId").asLong()).isEqualTo(user);
		assertThat(body.path("role").asString()).isEqualTo(role);
		assertThat(body.path("status").asString()).isEqualTo(status);
		assertThat(body.path("nextStep").asString()).isEqualTo(nextStep);
		assertThat(body.has("completedAt")).isTrue();
		if (completedAt == null) {
			assertThat(body.path("completedAt").isNull()).isTrue();
		} else {
			assertThat(Instant.parse(body.path("completedAt").asString())).isEqualTo(completedAt);
		}
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private String token(long user, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(user))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private static String signingKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
