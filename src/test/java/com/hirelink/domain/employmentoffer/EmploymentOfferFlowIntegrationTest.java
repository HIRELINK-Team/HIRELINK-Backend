package com.hirelink.domain.employmentoffer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(EmploymentOfferFlowIntegrationTest.TestClockConfiguration.class)
class EmploymentOfferFlowIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final Instant PROCESSING_TIME = Instant.parse("2026-11-01T03:00:00.123456Z");

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
		jdbc.execute("drop trigger if exists fail_employment_match_insert on employment_match");
		jdbc.execute("drop function if exists fail_employment_match_insert()");
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void createsOfferThenAcceptsAndCreatesMatchWithoutEndingOtherApplications() throws Exception {
		Fixture fixture = createApplication("WAITING_RESPONSE");
		long otherPosting = createPosting(fixture.employerId());
		long otherApplication = createApplication(fixture.studentId(), otherPosting, "WAITING_RESPONSE");

		HttpResponse<String> createResponse = createOffer(token(fixture.employerId(), "EMPLOYER"),
				Long.toString(fixture.applicationId()));

		assertThat(createResponse.statusCode()).as(createResponse.body()).isEqualTo(201);
		assertThat(createResponse.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode created = mapper.readTree(createResponse.body());
		assertThat(created.propertyNames()).containsExactlyInAnyOrder("offerId", "applicationId", "offerStatus",
				"applicationStatus", "studentResponseDeadline", "offeredAt");
		assertThat(created.path("offerStatus").asString()).isEqualTo("PENDING");
		assertThat(created.path("applicationStatus").asString()).isEqualTo("OFFERED");
		assertThat(Instant.parse(created.path("offeredAt").asString())).isEqualTo(PROCESSING_TIME);
		assertThat(Instant.parse(created.path("studentResponseDeadline").asString()))
				.isEqualTo(PROCESSING_TIME.plusSeconds(86400));

		long offerId = created.path("offerId").asLong();
		HttpResponse<String> acceptResponse = acceptOffer(token(fixture.studentId(), "STUDENT"),
				Long.toString(offerId));

		assertThat(acceptResponse.statusCode()).as(acceptResponse.body()).isEqualTo(200);
		assertThat(acceptResponse.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode accepted = mapper.readTree(acceptResponse.body());
		assertThat(accepted.propertyNames()).containsExactlyInAnyOrder("offerId", "applicationId", "employmentMatchId",
				"offerStatus", "applicationStatus", "respondedAt", "completedAt");
		assertThat(accepted.path("offerStatus").asString()).isEqualTo("ACCEPTED");
		assertThat(accepted.path("applicationStatus").asString()).isEqualTo("COMPLETED");
		assertThat(Instant.parse(accepted.path("respondedAt").asString())).isEqualTo(PROCESSING_TIME);
		assertThat(Instant.parse(accepted.path("completedAt").asString())).isEqualTo(PROCESSING_TIME);
		assertThat(jdbc.queryForObject("select status from application where id = ?", String.class,
				fixture.applicationId())).isEqualTo("COMPLETED");
		assertThat(jdbc.queryForObject("select status from application where id = ?", String.class,
				otherApplication)).isEqualTo("WAITING_RESPONSE");
		assertThat(jdbc.queryForObject("select count(*) from employment_match where employment_offer_id = ?",
				Integer.class, offerId)).isEqualTo(1);
	}

	@Test
	void validatesEmployerAccountOwnershipAndApplicationStateWhenCreating() throws Exception {
		Fixture fixture = createApplication("WAITING_RESPONSE");
		long otherEmployer = createUser("EMPLOYER");

		assertError(createOffer(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.applicationId())),
				403, "FORBIDDEN");
		assertError(createOffer(token(otherEmployer, "EMPLOYER"), Long.toString(fixture.applicationId())),
				403, "FORBIDDEN");
		assertError(createOffer(token(fixture.employerId(), "EMPLOYER"), "99999"),
				404, "APPLICATION_NOT_FOUND");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", fixture.employerId());
		assertError(createOffer(token(fixture.employerId(), "EMPLOYER"), Long.toString(fixture.applicationId())),
				403, "WITHDRAWN_ACCOUNT");
		assertThat(jdbc.queryForObject("select count(*) from employment_offer", Integer.class)).isZero();
	}

	@Test
	void preventsDuplicateOfferAndRepeatedAcceptance() throws Exception {
		Fixture fixture = createApplication("WAITING_RESPONSE");
		String employerToken = token(fixture.employerId(), "EMPLOYER");
		JsonNode created = mapper.readTree(createOffer(employerToken, Long.toString(fixture.applicationId())).body());
		long offerId = created.path("offerId").asLong();

		assertError(createOffer(employerToken, Long.toString(fixture.applicationId())),
				409, "EMPLOYMENT_OFFER_STATE_CONFLICT");
		String studentToken = token(fixture.studentId(), "STUDENT");
		assertThat(acceptOffer(studentToken, Long.toString(offerId)).statusCode()).isEqualTo(200);
		assertError(acceptOffer(studentToken, Long.toString(offerId)),
				409, "EMPLOYMENT_OFFER_STATE_CONFLICT");
		assertThat(jdbc.queryForObject("select count(*) from employment_offer", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from employment_match", Integer.class)).isEqualTo(1);
	}

	@Test
	void validatesStudentOwnershipStateAndDeadlineWhenAccepting() throws Exception {
		Fixture fixture = createApplication("OFFERED");
		long offerId = createPendingOffer(fixture.applicationId(), PROCESSING_TIME);
		long otherStudent = createStudent();

		assertError(acceptOffer(token(fixture.employerId(), "EMPLOYER"), Long.toString(offerId)), 403, "FORBIDDEN");
		assertError(acceptOffer(token(otherStudent, "STUDENT"), Long.toString(offerId)), 403, "FORBIDDEN");
		assertError(acceptOffer(token(fixture.studentId(), "STUDENT"), "99999"),
				404, "EMPLOYMENT_OFFER_NOT_FOUND");
		assertError(acceptOffer(token(fixture.studentId(), "STUDENT"), Long.toString(offerId)),
				409, "EMPLOYMENT_OFFER_STATE_CONFLICT");
		assertThat(jdbc.queryForObject("select status from application where id = ?", String.class,
				fixture.applicationId())).isEqualTo("OFFERED");
		assertThat(jdbc.queryForObject("select status from employment_offer where id = ?", String.class,
				offerId)).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("select count(*) from employment_match", Integer.class)).isZero();
	}

	@Test
	void concurrentAcceptancesCreateOnlyOneMatch() throws Exception {
		Fixture fixture = createApplication("OFFERED");
		long offerId = createPendingOffer(fixture.applicationId(), PROCESSING_TIME.plusSeconds(1));
		HttpRequest request = acceptRequest(token(fixture.studentId(), "STUDENT"), Long.toString(offerId));
		CompletableFuture<HttpResponse<String>> first;
		CompletableFuture<HttpResponse<String>> second;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from application where id = ? for update")) {
				statement.setLong(1, fixture.applicationId());
				statement.executeQuery().close();
			}
			try {
				first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
				second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
						select count(*) from pg_stat_activity where datname = current_database()
						and wait_event_type = 'Lock' and query like '%application%'
						""", Integer.class)).isGreaterThanOrEqualTo(2));
			} finally {
				connection.rollback();
			}
		}

		List<HttpResponse<String>> responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
		assertThat(jdbc.queryForObject("select count(*) from employment_match", Integer.class)).isEqualTo(1);
	}

	@Test
	void rollsBackAcceptanceWhenMatchCreationFails() throws Exception {
		Fixture fixture = createApplication("OFFERED");
		long offerId = createPendingOffer(fixture.applicationId(), PROCESSING_TIME.plusSeconds(1));
		jdbc.execute("""
				create function fail_employment_match_insert() returns trigger
				language plpgsql as $$ begin raise exception 'forced match insert failure'; end; $$
				""");
		jdbc.execute("""
				create trigger fail_employment_match_insert before insert on employment_match
				for each row execute function fail_employment_match_insert()
				""");

		assertError(acceptOffer(token(fixture.studentId(), "STUDENT"), Long.toString(offerId)),
				500, "INTERNAL_SERVER_ERROR");
		assertThat(jdbc.queryForObject("select status from application where id = ?", String.class,
				fixture.applicationId())).isEqualTo("OFFERED");
		assertThat(jdbc.queryForObject("select status from employment_offer where id = ?", String.class,
				offerId)).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("select responded_at from employment_offer where id = ?", Object.class,
				offerId)).isNull();
		assertThat(jdbc.queryForObject("select count(*) from employment_match", Integer.class)).isZero();
	}

	private Fixture createApplication(String status) {
		long student = createStudent();
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer);
		return new Fixture(student, employer, createApplication(student, posting, status));
	}

	private long createApplication(long student, long posting, String status) {
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 90.00, '{}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
		return jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, employer_response_deadline, applied_at)
				values (?, ?, ?, current_date, 1, '{}'::jsonb, ?, now() + interval '24 hours', now()) returning id
				""", Long.class, recommendation, student, posting, status);
	}

	private long createPendingOffer(long applicationId, Instant deadline) {
		return jdbc.queryForObject("""
				insert into employment_offer(application_id, status, student_response_deadline, offered_at)
				values (?, 'PENDING', ?, now()) returning id
				""", Long.class, applicationId, OffsetDateTime.ofInstant(deadline, ZoneOffset.UTC));
	}

	private long createStudent() {
		long student = createUser("STUDENT");
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-01-01', 'VN', '서울', 'INTERMEDIATE')
				""", student);
		return student;
	}

	private long createPosting(long employer) {
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '테스트 사업장', '테스트 대표자', 'test/certificate.pdf', ?, 1,
				'서울', 37.1234567, 127.1234567, 'UNDER_5') returning id
				""", Long.class, employer, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		return jdbc.queryForObject("insert into job_posting(workplace_id, status) values (?, 'RECRUITING') returning id",
				Long.class, workplace);
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private HttpResponse<String> createOffer(String token, String id) throws Exception {
		return client.send(request(token, "/api/applications/" + id + "/offers", "POST"),
				HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> acceptOffer(String token, String id) throws Exception {
		return client.send(acceptRequest(token, id), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest acceptRequest(String token, String id) {
		return request(token, "/api/offers/" + id + "/accept", "PATCH");
	}

	private HttpRequest request(String token, String path, String method) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(15)).method(method, HttpRequest.BodyPublishers.noBody());
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return request.build();
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private record Fixture(long studentId, long employerId, long applicationId) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(PROCESSING_TIME, ZoneOffset.UTC);
		}
	}

	private static String createTestSigningKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
