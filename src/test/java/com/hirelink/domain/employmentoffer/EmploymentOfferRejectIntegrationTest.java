package com.hirelink.domain.employmentoffer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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

import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmploymentOfferRejectIntegrationTest.TestClockConfiguration.class)
class EmploymentOfferRejectIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final Instant PROCESSING_TIME = Instant.parse("2026-10-31T12:00:00.123456Z");

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
		jdbc.execute("drop trigger if exists fail_employment_offer_update on employment_offer");
		jdbc.execute("drop function if exists fail_employment_offer_update()");
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void rejectsOwnPendingOfferBeforeDeadline() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));

		HttpResponse<String> response = reject(token(fixture.studentId(), "STUDENT"),
				Long.toString(fixture.offerId()));

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("offerId", "applicationId", "offerStatus",
				"applicationStatus", "endReason", "respondedAt", "endedAt");
		assertThat(body.path("offerId").asLong()).isEqualTo(fixture.offerId());
		assertThat(body.path("applicationId").asLong()).isEqualTo(fixture.applicationId());
		assertThat(body.path("offerStatus").asString()).isEqualTo("REJECTED");
		assertThat(body.path("applicationStatus").asString()).isEqualTo("ENDED");
		assertThat(body.path("endReason").asString()).isEqualTo("STUDENT_REJECTED");
		assertThat(Instant.parse(body.path("respondedAt").asString())).isEqualTo(PROCESSING_TIME);
		assertThat(Instant.parse(body.path("endedAt").asString())).isEqualTo(PROCESSING_TIME);

		assertRejectedState(fixture, PROCESSING_TIME);
		assertThat(jdbc.queryForObject("select count(*) from application where id = ?", Integer.class,
				fixture.applicationId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from employment_offer where id = ?", Integer.class,
				fixture.offerId())).isEqualTo(1);
	}

	@Test
	void requiresAuthenticationWithoutChangingState() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));
		State before = state(fixture);

		assertError(reject(null, Long.toString(fixture.offerId())), 401, "UNAUTHORIZED");
		assertError(reject("invalid-token", Long.toString(fixture.offerId())), 401, "UNAUTHORIZED");
		assertThat(state(fixture)).isEqualTo(before);
	}

	@Test
	void rejectsEmployerAccountWithoutChangingState() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));
		State before = state(fixture);

		assertError(reject(token(fixture.employerId(), "EMPLOYER"), Long.toString(fixture.offerId())),
				403, "FORBIDDEN");
		assertThat(state(fixture)).isEqualTo(before);
	}

	@Test
	void rejectsAnotherStudentsOfferWithoutChangingState() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));
		long otherStudent = createStudent();
		State before = state(fixture);

		assertError(reject(token(otherStudent, "STUDENT"), Long.toString(fixture.offerId())),
				403, "FORBIDDEN");
		assertThat(state(fixture)).isEqualTo(before);
	}

	@Test
	void returnsNotFoundForMissingOffer() throws Exception {
		long student = createStudent();

		assertError(reject(token(student, "STUDENT"), "99999"),
				404, "EMPLOYMENT_OFFER_NOT_FOUND");
	}

	@ParameterizedTest
	@ValueSource(strings = {"invalid", "1.5", "9223372036854775808", "0", "-1"})
	void rejectsInvalidOfferId(String id) throws Exception {
		long student = createStudent();

		assertError(reject(token(student, "STUDENT"), id), 400, "INVALID_INPUT");
	}

	@ParameterizedTest
	@EnumSource(value = EmploymentOfferStatus.class, names = {"ACCEPTED", "REJECTED", "EXPIRED"})
	void rejectsOffersOutsidePendingWithoutChangingState(EmploymentOfferStatus offerStatus) throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, offerStatus, PROCESSING_TIME.plusSeconds(1));
		State before = state(fixture);

		assertError(reject(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.offerId())),
				409, "EMPLOYMENT_OFFER_STATE_CONFLICT");
		assertThat(state(fixture)).isEqualTo(before);
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class, names = {"WAITING_RESPONSE", "COMPLETED", "ENDED"})
	void rejectsOffersWhoseApplicationIsNotOfferedWithoutChangingState(ApplicationStatus applicationStatus)
			throws Exception {
		Fixture fixture = createOffer(applicationStatus, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));
		State before = state(fixture);

		assertError(reject(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.offerId())),
				409, "EMPLOYMENT_OFFER_STATE_CONFLICT");
		assertThat(state(fixture)).isEqualTo(before);
	}

	@Test
	void acceptsRejectionOneMicrosecondBeforeDeadline() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusNanos(1_000));

		assertThat(reject(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.offerId())).statusCode())
				.isEqualTo(200);
		assertRejectedState(fixture, PROCESSING_TIME);
	}

	@Test
	void rejectsAtExactDeadlineWithoutChangingState() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING, PROCESSING_TIME);
		State before = state(fixture);

		assertError(reject(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.offerId())),
				409, "EMPLOYMENT_OFFER_STATE_CONFLICT");
		assertThat(state(fixture)).isEqualTo(before);
	}

	@Test
	void rejectsAfterDeadlineWithoutChangingState() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.minusNanos(1_000));
		State before = state(fixture);

		assertError(reject(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.offerId())),
				409, "EMPLOYMENT_OFFER_STATE_CONFLICT");
		assertThat(state(fixture)).isEqualTo(before);
	}

	@Test
	void rejectsRepeatedRejectionAndKeepsFirstResult() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));
		String accessToken = token(fixture.studentId(), "STUDENT");

		assertThat(reject(accessToken, Long.toString(fixture.offerId())).statusCode()).isEqualTo(200);
		State rejected = state(fixture);
		assertError(reject(accessToken, Long.toString(fixture.offerId())),
				409, "EMPLOYMENT_OFFER_STATE_CONFLICT");

		assertThat(state(fixture)).isEqualTo(rejected);
		assertThat(rejected.offer()).containsEntry("end_reason", "STUDENT_REJECTED");
		assertThat(instant(rejected.offer(), "responded_at")).isEqualTo(PROCESSING_TIME);
		assertThat(instant(rejected.offer(), "ended_at")).isEqualTo(PROCESSING_TIME);
		assertThat(instant(rejected.application(), "ended_at")).isEqualTo(PROCESSING_TIME);
	}

	@Test
	void validatesCurrentAccountExistenceAndWithdrawalState() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));
		State before = state(fixture);

		assertError(reject(token(99999, "STUDENT"), Long.toString(fixture.offerId())),
				401, "UNAUTHORIZED");
		jdbc.update("update user_account set withdrawn_at = now() where id = ?", fixture.studentId());
		assertError(reject(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.offerId())),
				403, "WITHDRAWN_ACCOUNT");
		assertThat(state(fixture)).isEqualTo(before);
	}

	@Test
	void concurrentRejectionsProduceOneSuccessAndOneConflict() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));
		String accessToken = token(fixture.studentId(), "STUDENT");
		CompletableFuture<HttpResponse<String>> first;
		CompletableFuture<HttpResponse<String>> second;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from application where id = ? for update")) {
				statement.setLong(1, fixture.applicationId());
				statement.executeQuery().close();
			}
			try {
				HttpRequest request = request(accessToken, Long.toString(fixture.offerId()));
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
		HttpResponse<String> success = responses.stream().filter(response -> response.statusCode() == 200)
				.findFirst().orElseThrow();
		HttpResponse<String> conflict = responses.stream().filter(response -> response.statusCode() == 409)
				.findFirst().orElseThrow();
		assertError(conflict, 409, "EMPLOYMENT_OFFER_STATE_CONFLICT");

		JsonNode successBody = mapper.readTree(success.body());
		assertThat(successBody.path("endReason").asString()).isEqualTo("STUDENT_REJECTED");
		assertRejectedState(fixture, Instant.parse(successBody.path("endedAt").asString()));
	}

	@Test
	void rollsBackApplicationWhenOfferUpdateFails() throws Exception {
		Fixture fixture = createOffer(ApplicationStatus.OFFERED, EmploymentOfferStatus.PENDING,
				PROCESSING_TIME.plusSeconds(1));
		State before = state(fixture);
		jdbc.execute("""
				create function fail_employment_offer_update() returns trigger
				language plpgsql as $$
				begin
					raise exception 'forced employment offer update failure';
				end;
				$$
				""");
		jdbc.execute("""
				create trigger fail_employment_offer_update before update on employment_offer
				for each row execute function fail_employment_offer_update()
				""");

		assertError(reject(token(fixture.studentId(), "STUDENT"), Long.toString(fixture.offerId())),
				500, "INTERNAL_SERVER_ERROR");

		assertThat(state(fixture)).isEqualTo(before);
	}

	private Fixture createOffer(ApplicationStatus applicationStatus, EmploymentOfferStatus offerStatus,
			Instant studentResponseDeadline) {
		long student = createStudent();
		long employer = createUser("EMPLOYER");
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_file_key, business_registration_number, business_type_id,
				workplace_address, latitude, longitude, employee_count_range)
				values (?, '테스트 사업장', '테스트 대표자', 'test/certificate.pdf', ?, 1,
				'서울', 37.1234567, 127.1234567, 'UNDER_5') returning id
				""", Long.class, employer, "TEST-" + UUID.randomUUID().toString().substring(0, 8));
		long posting = jdbc.queryForObject(
				"insert into job_posting(workplace_id, status) values (?, 'RECRUITING') returning id",
				Long.class, workplace);
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, reason_json, created_at)
				values (?, ?, 1, 90.00, '{}'::jsonb, now()) returning id
				""", Long.class, batch, posting);
		String applicationEndReason = applicationStatus == ApplicationStatus.ENDED ? "WITHDRAWN" : null;
		Long application = jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, end_reason, employer_response_deadline, applied_at, ended_at)
				values (?, ?, ?, current_date, 1, '{}'::jsonb, ?, ?, now() + interval '24 hours', now(),
				case when ? then now() else null end) returning id
				""", Long.class, recommendation, student, posting, applicationStatus.name(), applicationEndReason,
				applicationStatus == ApplicationStatus.ENDED);

		String offerEndReason = switch (offerStatus) {
			case REJECTED -> "STUDENT_REJECTED";
			case EXPIRED -> "STUDENT_RESPONSE_EXPIRED";
			default -> null;
		};
		boolean responded = offerStatus == EmploymentOfferStatus.ACCEPTED
				|| offerStatus == EmploymentOfferStatus.REJECTED;
		boolean ended = offerStatus == EmploymentOfferStatus.REJECTED
				|| offerStatus == EmploymentOfferStatus.EXPIRED;
		Long offer = jdbc.queryForObject("""
				insert into employment_offer(application_id, status, end_reason, student_response_deadline,
				offered_at, responded_at, ended_at)
				values (?, ?, ?, ?, ?, case when ? then ? else null end, case when ? then ? else null end)
				returning id
				""", Long.class, application, offerStatus.name(), offerEndReason,
				OffsetDateTime.ofInstant(studentResponseDeadline, ZoneOffset.UTC),
				OffsetDateTime.ofInstant(PROCESSING_TIME.minusSeconds(3600), ZoneOffset.UTC),
				responded, OffsetDateTime.ofInstant(PROCESSING_TIME.minusSeconds(60), ZoneOffset.UTC),
				ended, OffsetDateTime.ofInstant(PROCESSING_TIME.minusSeconds(60), ZoneOffset.UTC));
		return new Fixture(student, employer, application, offer);
	}

	private long createStudent() {
		long student = createUser("STUDENT");
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-01-01', 'VN', '서울', 'INTERMEDIATE')
				""", student);
		return student;
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private State state(Fixture fixture) {
		return new State(jdbc.queryForMap("select * from application where id = ?", fixture.applicationId()),
				jdbc.queryForMap("select * from employment_offer where id = ?", fixture.offerId()));
	}

	private void assertRejectedState(Fixture fixture, Instant processedAt) {
		State state = state(fixture);
		assertThat(state.application()).containsEntry("status", "ENDED")
				.containsEntry("end_reason", "STUDENT_REJECTED");
		assertThat(state.offer()).containsEntry("status", "REJECTED")
				.containsEntry("end_reason", "STUDENT_REJECTED");
		assertThat(instant(state.application(), "ended_at")).isEqualTo(processedAt);
		assertThat(instant(state.offer(), "responded_at")).isEqualTo(processedAt);
		assertThat(instant(state.offer(), "ended_at")).isEqualTo(processedAt);
	}

	private Instant instant(Map<String, Object> row, String column) {
		Object value = row.get(column);
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime.toInstant();
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant();
		}
		return (Instant)value;
	}

	private HttpResponse<String> reject(String token, String id) throws Exception {
		return client.send(request(token, id), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest request(String token, String id) {
		HttpRequest.Builder request = HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + "/api/offers/" + id + "/reject"))
				.timeout(Duration.ofSeconds(15)).method("PATCH", HttpRequest.BodyPublishers.noBody());
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
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		JsonNode body = mapper.readTree(response.body());
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}

	private record Fixture(long studentId, long employerId, long applicationId, long offerId) {
	}

	private record State(Map<String, Object> application, Map<String, Object> offer) {
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
