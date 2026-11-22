package com.hirelink.domain.employmentnotification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
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

import com.hirelink.support.PostgreSQLIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmploymentPopupNotificationIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();
	private static final Instant OLDEST = Instant.parse("2026-11-20T01:00:00Z");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> TEST_JWT_SECRET);
	}

	@LocalServerPort private int port;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;
	@Autowired private JwtEncoder jwtEncoder;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE student_enrollment_document, student_document_cleanup, employment_popup_notification, employment_match, employment_offer, application, "
				+ "recommendation, recommendation_batch, employer_profile, student_preferred_job, "
				+ "student_preferred_work_time, student_preferred_location, student_job_preference, student_priority, "
				+ "job_posting_work_time, job_posting, workplace, student_career, student_profile, user_term_agreement, "
				+ "phone_verification, phone_verification_send_attempt, refresh_token, auth_account, "
				+ "student_registration_verification, user_account RESTART IDENTITY");
	}

	@Test
	void returnsOnlyOwnUnconfirmedNotificationsInCreationOrder() throws Exception {
		Fixture fixture = fixture();
		long oldest = insertApplicationNotification(fixture.employerId(), fixture.applicationId(),
				"APPLICATION_CREATED", OLDEST, null);
		long sameTimeSecond = insertOfferNotification(fixture.employerId(), fixture.offerId(),
				"OFFER_ACCEPTED", OLDEST, null);
		insertOfferNotification(fixture.employerId(), fixture.offerId(),
				"OFFER_REJECTED", OLDEST.plusSeconds(2), OLDEST.plusSeconds(3));
		insertOfferNotification(fixture.studentId(), fixture.offerId(),
				"OFFER_CREATED", OLDEST.minusSeconds(1), null);

		HttpResponse<String> response = get(client, token(fixture.employerId(), "EMPLOYER"));

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode notifications = mapper.readTree(response.body()).path("notifications");
		assertThat(notifications).hasSize(2);
		assertThat(notifications.get(0).path("notificationId").asLong()).isEqualTo(oldest);
		assertThat(notifications.get(0).path("type").asString()).isEqualTo("APPLICATION_CREATED");
		assertThat(notifications.get(0).path("message").asString())
				.isEqualTo("새로운 지원자가 있습니다. 서비스에서 확인해 주세요.");
		assertThat(notifications.get(0).path("applicationId").asLong()).isEqualTo(fixture.applicationId());
		assertThat(notifications.get(0).path("employmentOfferId").isNull()).isTrue();
		assertThat(notifications.get(1).path("notificationId").asLong()).isEqualTo(sameTimeSecond);
		assertThat(notifications.get(1).path("employmentOfferId").asLong()).isEqualTo(fixture.offerId());
	}

	@Test
	void returnsEmptyArrayWhenThereAreNoUnconfirmedNotifications() throws Exception {
		long user = createUser("STUDENT");

		HttpResponse<String> response = get(client, token(user, "STUDENT"));

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(mapper.readTree(response.body()).path("notifications")).isEmpty();
	}

	@Test
	void confirmationIsIdempotentAndHidesPopupFromAnotherClient() throws Exception {
		Fixture fixture = fixture();
		long notification = insertOfferNotification(fixture.studentId(), fixture.offerId(),
				"OFFER_CREATED", OLDEST, null);
		String accessToken = token(fixture.studentId(), "STUDENT");

		HttpResponse<String> first = confirm(client, accessToken, Long.toString(notification));
		Instant confirmedAt = Instant.parse(mapper.readTree(first.body()).path("confirmedAt").asString());
		HttpClient anotherDevice = HttpClient.newHttpClient();
		HttpResponse<String> repeated = confirm(anotherDevice, accessToken, Long.toString(notification));

		assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
		assertThat(repeated.statusCode()).as(repeated.body()).isEqualTo(200);
		assertThat(Instant.parse(mapper.readTree(repeated.body()).path("confirmedAt").asString()))
				.isEqualTo(confirmedAt);
		assertThat(mapper.readTree(get(anotherDevice, accessToken).body()).path("notifications")).isEmpty();
		assertThat(jdbc.queryForObject("select confirmed_at is not null from employment_popup_notification where id = ?",
				Boolean.class, notification)).isTrue();
	}

	@Test
	void blocksOtherUsersAndHandlesMissingInvalidAndUnauthenticatedRequests() throws Exception {
		Fixture fixture = fixture();
		long notification = insertApplicationNotification(fixture.employerId(), fixture.applicationId(),
				"APPLICATION_CREATED", OLDEST, null);

		assertError(confirm(client, token(fixture.studentId(), "STUDENT"), Long.toString(notification)),
				403, "FORBIDDEN");
		assertError(confirm(client, token(fixture.employerId(), "EMPLOYER"), "99999"),
				404, "EMPLOYMENT_POPUP_NOTIFICATION_NOT_FOUND");
		assertError(confirm(client, token(fixture.employerId(), "EMPLOYER"), "0"), 400, "INVALID_INPUT");
		assertError(get(client, null), 401, "UNAUTHORIZED");
		assertThat(jdbc.queryForObject("select confirmed_at from employment_popup_notification where id = ?",
				Object.class, notification)).isNull();
	}

	@Test
	void databaseConstraintAllowsOnlyOneConcurrentNotificationForSameEventAndRecipient() throws Exception {
		Fixture fixture = fixture();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		CompletableFuture<Throwable> first = insertConcurrently(ready, start, fixture);
		CompletableFuture<Throwable> second = insertConcurrently(ready, start, fixture);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

		start.countDown();
		List<Throwable> failures = Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
				.stream().filter(error -> error != null).toList();

		assertThat(failures).hasSize(1);
		assertThat(failures.get(0)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("select count(*) from employment_popup_notification",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void databaseRejectsNotificationWithBothApplicationAndOfferReferences() {
		Fixture fixture = fixture();

		assertThatThrownBy(() -> jdbc.update("""
				insert into employment_popup_notification(recipient_user_id, notification_type,
				application_id, employment_offer_id, created_at)
				values (?, 'OFFER_CREATED', ?, ?, now())
				""", fixture.studentId(), fixture.applicationId(), fixture.offerId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void openApiDocumentsPopupQueryAndConfirmation() throws Exception {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		JsonNode paths = mapper.readTree(response.body()).path("paths");
		assertThat(paths.path("/api/notifications/popups").path("get").isObject()).isTrue();
		JsonNode confirmation = paths.path("/api/notifications/popups/{notificationId}/confirm").path("patch");
		assertThat(confirmation.isObject()).isTrue();
		assertThat(confirmation.path("responses").propertyNames())
				.contains("200", "400", "401", "403", "404");
	}

	private CompletableFuture<Throwable> insertConcurrently(CountDownLatch ready, CountDownLatch start,
			Fixture fixture) {
		return CompletableFuture.supplyAsync(() -> {
			ready.countDown();
			try {
				start.await(5, TimeUnit.SECONDS);
				insertApplicationNotification(fixture.employerId(), fixture.applicationId(),
						"APPLICATION_CREATED", OLDEST, null);
				return null;
			} catch (Throwable exception) {
				return exception;
			}
		});
	}

	private Fixture fixture() {
		long employer = createUser("EMPLOYER");
		long student = createUser("STUDENT");
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-01-01', 'VN', '서울', 'INTERMEDIATE')
				""", student);
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
				business_registration_number, business_type_id, workplace_address, latitude, longitude,
				employee_count_range)
				values (?, '테스트 사업장', '대표자', ?, 1, '서울', 37.1234567, 127.1234567, 'UNDER_5')
				returning id
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
		long application = jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, employer_response_deadline, applied_at)
				values (?, ?, ?, current_date, 1, '{}'::jsonb, 'OFFERED', now() + interval '24 hours', now())
				returning id
				""", Long.class, recommendation, student, posting);
		long offer = jdbc.queryForObject("""
				insert into employment_offer(application_id, status, student_response_deadline, offered_at)
				values (?, 'PENDING', now() + interval '24 hours', now()) returning id
				""", Long.class, application);
		return new Fixture(student, employer, application, offer);
	}

	private long createUser(String role) {
		return jdbc.queryForObject("insert into user_account(email, role) values (?, ?) returning id",
				Long.class, UUID.randomUUID() + "@example.com", role);
	}

	private long insertApplicationNotification(long recipient, long application, String type,
			Instant createdAt, Instant confirmedAt) {
		return jdbc.queryForObject("""
				insert into employment_popup_notification(recipient_user_id, notification_type,
				application_id, created_at, confirmed_at) values (?, ?, ?, ?, ?) returning id
				""", Long.class, recipient, type, application, timestamp(createdAt), timestamp(confirmedAt));
	}

	private long insertOfferNotification(long recipient, long offer, String type,
			Instant createdAt, Instant confirmedAt) {
		return jdbc.queryForObject("""
				insert into employment_popup_notification(recipient_user_id, notification_type,
				employment_offer_id, created_at, confirmed_at) values (?, ?, ?, ?, ?) returning id
				""", Long.class, recipient, type, offer, timestamp(createdAt), timestamp(confirmedAt));
	}

	private OffsetDateTime timestamp(Instant instant) {
		return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private HttpResponse<String> get(HttpClient httpClient, String token) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/notifications/popups")).GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> confirm(HttpClient httpClient, String token, String id) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/notifications/popups/" + id + "/confirm"))
				.method("PATCH", HttpRequest.BodyPublishers.noBody());
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo(code);
	}

	private static String createTestSigningKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

	private record Fixture(long studentId, long employerId, long applicationId, long offerId) {
	}
}
