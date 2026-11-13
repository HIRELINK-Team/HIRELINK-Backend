package com.hirelink.domain.recommendation;

import com.hirelink.support.PostgreSQLIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
class RecommendationApiIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String TEST_JWT_SECRET = createTestSigningKey();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
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
		jdbc.execute("TRUNCATE TABLE employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, phone_verification_send_attempt, "
				+ "refresh_token, auth_account, user_account RESTART IDENTITY");
	}

	@Test
	void createsAtMostThreeRecommendationsAndPersistsV1SnapshotAndReasons() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		for (int index = 0; index < 4; index++) {
			createPosting(employer, "공고 " + index, 13000 + index, "RECRUITING");
		}

		JsonNode body = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);

		assertThat(body.propertyNames()).containsExactlyInAnyOrder(
				"batchId", "generatedAt", "recommendationCount");
		assertThat(body.path("recommendationCount").asInt()).isEqualTo(3);
		Long batchId = body.path("batchId").asLong();
		assertThat(jdbc.queryForObject("select algorithm_version from recommendation_batch where id = ?",
				String.class, batchId)).isEqualTo("v1");
		JsonNode snapshot = mapper.readTree(jdbc.queryForObject(
				"select input_snapshot::text from recommendation_batch where id = ?", String.class, batchId));
		assertThat(snapshot.path("priorities").size()).isEqualTo(4);
		assertThat(snapshot.path("preferredLocation").path("radiusM").asString()).isEqualTo("3000");
		assertThat(jdbc.queryForList(
				"select rank from recommendation where batch_id = ? order by rank", Short.class, batchId))
				.containsExactly((short)1, (short)2, (short)3);
		JsonNode reason = mapper.readTree(jdbc.queryForObject(
				"select reason_json::text from recommendation where batch_id = ? and rank = 1",
				String.class, batchId));
		assertThat(reason.path("recommendationReasons").isArray()).isTrue();
		assertThat(reason.path("criterionScores").isObject()).isTrue();
	}

	@Test
	void createsEmptyBatchAndRejectsEveryLaterInitialCreation() throws Exception {
		long student = createStudent(true);

		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);

		assertThat(created.path("recommendationCount").asInt()).isZero();
		assertThat(count("recommendation_batch")).isOne();
		assertThat(count("recommendation")).isZero();
		assertError(post(token(student, "STUDENT"), "/api/recommendations"),
				409, "RECOMMENDATION_STATE_CONFLICT");
		assertThat(count("recommendation_batch")).isOne();
	}

	@Test
	void rejectsMissingRecommendationPrerequisites() throws Exception {
		long student = createStudent(false);

		assertError(post(token(student, "STUDENT"), "/api/recommendations"),
				409, "RECOMMENDATION_STATE_CONFLICT");
		assertThat(count("recommendation_batch")).isZero();
	}

	@Test
	void concurrentInitialCreationStoresOnlyOneBatch() {
		long student = createStudent(true);
		String token = token(student, "STUDENT");
		List<CompletableFuture<HttpResponse<String>>> requests = List.of(
				postAsync(token, "/api/recommendations"), postAsync(token, "/api/recommendations"));

		List<Integer> statuses = requests.stream().map(CompletableFuture::join)
				.map(HttpResponse::statusCode).sorted().toList();

		assertThat(statuses).containsExactly(201, 409);
		assertThat(count("recommendation_batch")).isOne();
	}

	@Test
	void refreshReturnsExistingBatchWhenContextHasNotChanged() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		createPosting(employer, "기존 공고", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);

		JsonNode refreshed = body(post(token(student, "STUDENT"), "/api/recommendations/refresh"), 200);

		assertThat(refreshed.propertyNames()).containsExactlyInAnyOrder(
				"batchId", "generatedAt", "recommendationCount", "refreshed");
		assertThat(refreshed.path("batchId").asLong()).isEqualTo(created.path("batchId").asLong());
		assertThat(refreshed.path("recommendationCount").asInt()).isEqualTo(1);
		assertThat(refreshed.path("refreshed").asBoolean()).isFalse();
		assertThat(count("recommendation_batch")).isOne();
	}

	@Test
	void refreshCreatesNewBatchAndPreservesInvalidatedHistoryEvenWhenResultBecomesEmpty() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "마감될 공고", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		long oldRecommendation = jdbc.queryForObject(
				"select id from recommendation where batch_id = ?", Long.class, created.path("batchId").asLong());
		jdbc.update("update job_posting set status = 'CLOSED' where id = ?", posting);

		JsonNode refreshed = body(post(token(student, "STUDENT"), "/api/recommendations/refresh"), 200);

		assertThat(refreshed.path("refreshed").asBoolean()).isTrue();
		assertThat(refreshed.path("batchId").asLong()).isNotEqualTo(created.path("batchId").asLong());
		assertThat(refreshed.path("recommendationCount").asInt()).isZero();
		assertThat(count("recommendation_batch")).isEqualTo(2);
		assertThat(count("recommendation")).isOne();
		assertThat(jdbc.queryForObject("select invalidated_at is not null from recommendation_batch where id = ?",
				Boolean.class, created.path("batchId").asLong())).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from recommendation where id = ?", Integer.class,
				oldRecommendation)).isOne();
	}

	@Test
	void matchingConditionAndPriorityChangesUseSameValidityDecisionForGetAndRefresh() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "조건 감지 공고", 13000, "RECRUITING");
		body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		long recommendation = jdbc.queryForObject(
				"select id from recommendation where job_posting_id = ?", Long.class, posting);

		jdbc.update("update student_job_preference set minimum_hourly_wage = 14000 where user_id = ?", student);
		assertRefreshRequiredForListAndDetail(student, recommendation);
		assertThat(body(post(token(student, "STUDENT"), "/api/recommendations/refresh"), 200)
				.path("refreshed").asBoolean()).isTrue();

		jdbc.update("update student_priority set priority_order = priority_order + 10 where user_id = ?", student);
		jdbc.update("update student_priority set priority_order = 5 - (priority_order - 10) where user_id = ?", student);
		JsonNode list = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);
		assertThat(list.path("refreshRequired").asBoolean()).isTrue();
		assertThat(body(post(token(student, "STUDENT"), "/api/recommendations/refresh"), 200)
				.path("refreshed").asBoolean()).isTrue();
	}

	@Test
	void detectsPostingConditionStatusDeadlineAndWorkplaceLocationChanges() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "공고 변경 감지", 13000, "RECRUITING");
		body(post(token(student, "STUDENT"), "/api/recommendations"), 201);

		jdbc.update("update job_posting set wage_amount = 15000 where id = ?", posting);
		assertRefreshed(student);
		jdbc.update("update job_posting set status = 'CLOSED' where id = ?", posting);
		assertRefreshed(student);
		jdbc.update("update job_posting set status = 'RECRUITING', application_deadline_date = current_date - 1 where id = ?",
				posting);
		assertRefreshed(student);
		jdbc.update("update job_posting set application_deadline_date = current_date + 7 where id = ?", posting);
		assertRefreshed(student);
		jdbc.update("update workplace set latitude = latitude + 0.01 where id = "
				+ "(select workplace_id from job_posting where id = ?)", posting);
		assertRefreshed(student);
	}

	@Test
	void detectsAnotherStudentsActiveReviewCountChange() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "지원 변경 감지", 13000, "RECRUITING");
		body(post(token(student, "STUDENT"), "/api/recommendations"), 201);

		insertApplicationForNewStudent(posting, "WAITING_RESPONSE");
		assertRefreshed(student);
	}

	@ParameterizedTest
	@ValueSource(strings = {"WAITING_RESPONSE", "OFFERED"})
	void keepsLatestRecommendationsWhileApplicationIsActive(String status) throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "진행 중 지원 공고", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		long recommendation = jdbc.queryForObject(
				"select id from recommendation where job_posting_id = ?", Long.class, posting);
		insertApplication(student, recommendation, posting, status);
		long application = jdbc.queryForObject(
				"select id from application where recommendation_id = ?", Long.class, recommendation);

		JsonNode list = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);
		JsonNode item = list.path("recommendations").get(0);
		assertThat(list.path("refreshRequired").asBoolean()).isFalse();
		assertThat(item.path("applicationId").asLong()).isEqualTo(application);
		assertThat(item.path("applicationStatus").asString()).isEqualTo(status);
		assertThat(body(get(token(student, "STUDENT"), "/api/recommendations/" + recommendation), 200)
				.path("recommendationId").asLong()).isEqualTo(recommendation);

		JsonNode refreshed = body(post(token(student, "STUDENT"), "/api/recommendations/refresh"), 200);
		assertThat(refreshed.path("refreshed").asBoolean()).isFalse();
		assertThat(refreshed.path("batchId").asLong()).isEqualTo(created.path("batchId").asLong());
		assertThat(count("recommendation_batch")).isOne();
	}

	@ParameterizedTest
	@ValueSource(strings = {"ENDED", "COMPLETED"})
	void refreshesAndExcludesAppliedPostingWhenApplicationIsTerminal(String status) throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "종료된 지원 공고", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		long recommendation = jdbc.queryForObject(
				"select id from recommendation where job_posting_id = ?", Long.class, posting);
		insertApplication(student, recommendation, posting, status);

		JsonNode list = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);
		assertThat(list.path("refreshRequired").asBoolean()).isTrue();
		assertThat(list.path("recommendations").isEmpty()).isTrue();
		assertError(get(token(student, "STUDENT"), "/api/recommendations/" + recommendation),
				409, "RECOMMENDATION_STATE_CONFLICT");

		JsonNode refreshed = body(post(token(student, "STUDENT"), "/api/recommendations/refresh"), 200);
		assertThat(refreshed.path("refreshed").asBoolean()).isTrue();
		assertThat(refreshed.path("batchId").asLong()).isNotEqualTo(created.path("batchId").asLong());
		assertThat(refreshed.path("recommendationCount").asInt()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from recommendation where batch_id = ? and job_posting_id = ?",
				Integer.class, refreshed.path("batchId").asLong(), posting)).isZero();
	}

	@Test
	void detectsNewPostingFromEmptyBatchAndReturnedReviewSeat() throws Exception {
		long emptyStudent = createStudent(true);
		body(post(token(emptyStudent, "STUDENT"), "/api/recommendations"), 201);
		long employer = createUser("EMPLOYER");
		long newPosting = createPosting(employer, "새 공고", 13000, "RECRUITING");

		JsonNode newCandidateRefresh = body(post(token(emptyStudent, "STUDENT"),
				"/api/recommendations/refresh"), 200);
		assertThat(newCandidateRefresh.path("refreshed").asBoolean()).isTrue();
		assertThat(newCandidateRefresh.path("recommendationCount").asInt()).isEqualTo(1);

		for (int count = 0; count < 5; count++) {
			insertApplicationForNewStudent(newPosting, count == 0 ? "OFFERED" : "WAITING_RESPONSE");
		}
		long capacityStudent = createStudent(true);
		JsonNode emptyAtCapacity = body(post(token(capacityStudent, "STUDENT"), "/api/recommendations"), 201);
		assertThat(emptyAtCapacity.path("recommendationCount").asInt()).isZero();
		jdbc.update("update application set status = 'ENDED', end_reason = 'STUDENT_REJECTED', ended_at = now() "
				+ "where id = (select id from application where job_posting_id = ? and status = 'OFFERED' limit 1)",
				newPosting);

		JsonNode returnedSeatRefresh = body(post(token(capacityStudent, "STUDENT"),
				"/api/recommendations/refresh"), 200);
		assertThat(returnedSeatRefresh.path("refreshed").asBoolean()).isTrue();
		assertThat(returnedSeatRefresh.path("recommendationCount").asInt()).isEqualTo(1);
	}

	@Test
	void concurrentRefreshCreatesOnlyOneBatchForTheSameChange() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "동시 갱신", 13000, "RECRUITING");
		body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		jdbc.update("update job_posting set wage_amount = 14000 where id = ?", posting);
		String studentToken = token(student, "STUDENT");

		List<JsonNode> responses = List.of(
				postAsync(studentToken, "/api/recommendations/refresh"),
				postAsync(studentToken, "/api/recommendations/refresh"))
				.stream().map(CompletableFuture::join)
				.peek(response -> assertThat(response.statusCode()).as(response.body()).isEqualTo(200))
				.map(response -> {
					try {
						return mapper.readTree(response.body());
					} catch (Exception exception) {
						throw new IllegalStateException(exception);
					}
				})
				.toList();

		assertThat(responses.stream().map(response -> response.path("refreshed").asBoolean()).toList())
				.containsExactlyInAnyOrder(true, false);
		assertThat(jdbc.queryForObject("select count(*) from recommendation_batch where student_profile_id = ?",
				Integer.class, student)).isEqualTo(2);
	}

	@Test
	void listDoesNotAutoRefreshBeforeTwentyFourHours() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		createPosting(employer, "24시간 전 추천", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		jdbc.update("update recommendation_batch set created_at = now() - interval '23 hours 59 minutes' where id = ?",
				created.path("batchId").asLong());

		JsonNode listed = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);

		assertThat(listed.path("batchId").asLong()).isEqualTo(created.path("batchId").asLong());
		assertThat(count("recommendation_batch")).isOne();
	}

	@Test
	void expiredListAutoRefreshesAndPreservesRecommendationHistory() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		createPosting(employer, "24시간 만료 추천", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		long oldRecommendation = jdbc.queryForObject(
				"select id from recommendation where batch_id = ?", Long.class, created.path("batchId").asLong());
		jdbc.update("update recommendation_batch set created_at = now() - interval '24 hours' where id = ?",
				created.path("batchId").asLong());

		JsonNode listed = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);

		assertThat(listed.path("batchId").asLong()).isNotEqualTo(created.path("batchId").asLong());
		assertThat(listed.path("refreshRequired").asBoolean()).isFalse();
		assertThat(listed.path("recommendations").size()).isOne();
		assertThat(Instant.parse(listed.path("generatedAt").asText())).isAfter(Instant.parse(created.path("generatedAt").asText()));
		assertThat(count("recommendation_batch")).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from recommendation where id = ?", Integer.class,
				oldRecommendation)).isOne();
	}

	@Test
	void expiredListRetainsActiveRecommendationAndFillsOnlyAvailableSeats() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		for (int index = 0; index < 4; index++) {
			createPosting(employer, "부분 갱신 공고 " + index, 13000, "RECRUITING");
		}
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		JsonNode before = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);
		long retainedRecommendation = before.path("recommendations").get(0).path("recommendationId").asLong();
		long retainedPosting = before.path("recommendations").get(0).path("jobPostingId").asLong();
		insertApplication(student, retainedRecommendation, retainedPosting, "WAITING_RESPONSE");
		long application = jdbc.queryForObject(
				"select id from application where recommendation_id = ?", Long.class, retainedRecommendation);
		Instant responseDeadline = jdbc.queryForObject(
				"select employer_response_deadline from application where id = ?", Instant.class, application);
		for (int index = 1; index < before.path("recommendations").size(); index++) {
			jdbc.update("update job_posting set status = 'CLOSED' where id = ?",
					before.path("recommendations").get(index).path("jobPostingId").asLong());
		}
		jdbc.update("update recommendation_batch set created_at = now() - interval '24 hours 1 second' where id = ?",
				created.path("batchId").asLong());

		JsonNode listed = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);

		assertThat(listed.path("recommendations").size()).isEqualTo(2);
		JsonNode retained = listed.path("recommendations").get(0);
		assertThat(retained.path("jobPostingId").asLong()).isEqualTo(retainedPosting);
		assertThat(retained.path("applicationId").asLong()).isEqualTo(application);
		assertThat(retained.path("applicationStatus").asString()).isEqualTo("WAITING_RESPONSE");
		assertThat(body(get(token(student, "STUDENT"),
				"/api/recommendations/" + retained.path("recommendationId").asLong()), 200)
				.path("jobPostingId").asLong()).isEqualTo(retainedPosting);
		assertThat(jdbc.queryForObject("select employer_response_deadline from application where id = ?",
				Instant.class, application)).isEqualTo(responseDeadline);
	}

	@Test
	void expiredListReturnsEmptyNewBatchWhenNoCandidateExists() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "사라질 후보", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		jdbc.update("update job_posting set status = 'CLOSED' where id = ?", posting);
		jdbc.update("update recommendation_batch set created_at = now() - interval '25 hours' where id = ?",
				created.path("batchId").asLong());

		JsonNode listed = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);

		assertThat(listed.path("batchId").asLong()).isNotEqualTo(created.path("batchId").asLong());
		assertThat(listed.path("recommendations").isEmpty()).isTrue();
		assertThat(listed.path("refreshRequired").asBoolean()).isFalse();
		assertThat(count("recommendation")).isOne();
	}

	@Test
	void expiredListWithThreeActiveRecommendationsRefreshesOnlyOnce() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		for (int index = 0; index < 3; index++) {
			createPosting(employer, "모두 진행 중 " + index, 13000, "RECRUITING");
		}
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		JsonNode before = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);
		for (JsonNode item : before.path("recommendations")) {
			insertApplication(student, item.path("recommendationId").asLong(),
					item.path("jobPostingId").asLong(), "OFFERED");
		}
		jdbc.update("update recommendation_batch set created_at = now() - interval '25 hours' where id = ?",
				created.path("batchId").asLong());

		JsonNode first = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);
		JsonNode second = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);

		assertThat(first.path("recommendations").size()).isEqualTo(3);
		assertThat(first.path("recommendations")).allSatisfy(
				item -> assertThat(item.path("applicationStatus").asString()).isEqualTo("OFFERED"));
		assertThat(second.path("batchId").asLong()).isEqualTo(first.path("batchId").asLong());
		assertThat(count("recommendation_batch")).isEqualTo(2);
	}

	@Test
	void concurrentExpiredListCreatesOnlyOneNewBatch() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		createPosting(employer, "동시 자동 갱신", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		jdbc.update("update recommendation_batch set created_at = now() - interval '25 hours' where id = ?",
				created.path("batchId").asLong());
		String studentToken = token(student, "STUDENT");

		List<HttpResponse<String>> responses = List.of(
				getAsync(studentToken, "/api/recommendations"),
				getAsync(studentToken, "/api/recommendations"))
				.stream().map(CompletableFuture::join).toList();

		assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
		assertThat(responses.stream().map(response -> {
			try {
				return mapper.readTree(response.body()).path("batchId").asLong();
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		}).distinct().count()).isOne();
		assertThat(jdbc.queryForObject("select count(*) from recommendation_batch where student_profile_id = ?",
				Integer.class, student)).isEqualTo(2);
	}

	@Test
	void refreshWithoutExistingBatchReturnsNotFound() throws Exception {
		long student = createStudent(true);

		assertError(post(token(student, "STUDENT"), "/api/recommendations/refresh"),
				404, "RECOMMENDATION_NOT_FOUND");
	}

	@Test
	void listsLatestValidRecommendationsInRankOrderWithCurrentPostingData() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long firstPosting = createPosting(employer, "변경 전 제목", 14000, "RECRUITING");
		createPosting(employer, "두 번째 공고", 12000, "RECRUITING");
		body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		jdbc.update("update job_posting set title = '현재 제목' where id = ?", firstPosting);

		JsonNode body = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);

		assertThat(body.path("refreshRequired").asBoolean()).isFalse();
		assertThat(body.path("recommendations").size()).isEqualTo(2);
		assertThat(body.path("recommendations").get(0).path("rank").asInt()).isEqualTo(1);
		assertThat(body.path("recommendations").get(1).path("rank").asInt()).isEqualTo(2);
		assertThat(body.path("recommendations").get(0).path("applicationId").isNull()).isTrue();
		assertThat(body.path("recommendations").get(0).path("applicationStatus").isNull()).isTrue();
		List<String> titles = new ArrayList<>();
		body.path("recommendations").forEach(item -> titles.add(item.path("jobTitle").asString()));
		assertThat(titles).contains("현재 제목");
		assertThat(body.toString()).doesNotContain("phone", "representative", "businessRegistration");
	}

	@Test
	void distinguishesEmptyBatchFromMissingBatch() throws Exception {
		long emptyStudent = createStudent(true);
		long noBatchStudent = createStudent(true);
		body(post(token(emptyStudent, "STUDENT"), "/api/recommendations"), 201);

		JsonNode empty = body(get(token(emptyStudent, "STUDENT"), "/api/recommendations"), 200);
		assertThat(empty.path("refreshRequired").asBoolean()).isFalse();
		assertThat(empty.path("recommendations").isEmpty()).isTrue();
		long employer = createUser("EMPLOYER");
		createPosting(employer, "새로 등록된 공고", 13000, "RECRUITING");
		JsonNode changed = body(get(token(emptyStudent, "STUDENT"), "/api/recommendations"), 200);
		assertThat(changed.path("refreshRequired").asBoolean()).isTrue();
		assertThat(changed.path("recommendations").isEmpty()).isTrue();
		assertError(get(token(noBatchStudent, "STUDENT"), "/api/recommendations"),
				404, "RECOMMENDATION_NOT_FOUND");
	}

	@Test
	void returnsDetailFromStoredScoreAndReasonsAndBlocksAnotherStudent() throws Exception {
		long owner = createStudent(true);
		long other = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "상세 공고", 15000, "RECRUITING");
		body(post(token(owner, "STUDENT"), "/api/recommendations"), 201);
		long recommendation = jdbc.queryForObject(
				"select id from recommendation where job_posting_id = ?", Long.class, posting);
		String storedScore = jdbc.queryForObject("select score::text from recommendation where id = ?",
				String.class, recommendation);

		JsonNode detail = body(get(token(owner, "STUDENT"), "/api/recommendations/" + recommendation), 200);

		assertThat(detail.path("recommendationId").asLong()).isEqualTo(recommendation);
		assertThat(detail.path("matchScore").decimalValue()).isEqualByComparingTo(storedScore);
		assertThat(detail.path("recommendationReasons").isArray()).isTrue();
		assertThat(detail.path("workplaceName").asString()).isEqualTo("테스트 사업장");
		assertThat(detail.toString()).doesNotContain("phone", "representative", "businessRegistration");
		assertError(get(token(other, "STUDENT"), "/api/recommendations/" + recommendation),
				403, "FORBIDDEN");
	}

	@Test
	void requiresRefreshForChangedMatchingStateAndRejectsInvalidDetail() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "급여 변경 공고", 13000, "RECRUITING");
		body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		long recommendation = jdbc.queryForObject(
				"select id from recommendation where job_posting_id = ?", Long.class, posting);
		jdbc.update("update job_posting set wage_amount = 9000 where id = ?", posting);

		JsonNode list = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);

		assertThat(list.path("refreshRequired").asBoolean()).isTrue();
		assertThat(list.path("recommendations").isEmpty()).isTrue();
		assertError(get(token(student, "STUDENT"), "/api/recommendations/" + recommendation),
				409, "RECOMMENDATION_STATE_CONFLICT");
	}

	@Test
	void rejectsRecommendationFromOlderBatch() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		long posting = createPosting(employer, "이전 추천", 13000, "RECRUITING");
		JsonNode created = body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		long recommendation = jdbc.queryForObject(
				"select id from recommendation where job_posting_id = ?", Long.class, posting);
		jdbc.update("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				select student_profile_id, algorithm_version, input_snapshot, created_at + interval '1 second'
				from recommendation_batch where id = ?
				""", created.path("batchId").asLong());

		assertError(get(token(student, "STUDENT"), "/api/recommendations/" + recommendation),
				409, "RECOMMENDATION_STATE_CONFLICT");
	}

	@Test
	void connectsRecommendationApplicationOfferAcceptanceAndMatchQueries() throws Exception {
		long student = createStudent(true);
		long employer = createUser("EMPLOYER");
		jdbc.update("update user_account set name = '통합 유학생', phone_number = '01011111111', "
				+ "phone_verified_at = now() where id = ?", student);
		jdbc.update("update user_account set name = '통합 사업주', phone_number = '01022222222', "
				+ "phone_verified_at = now() where id = ?", employer);
		long posting = createPosting(employer, "연결 검증 공고", 13000, "RECRUITING");
		String studentToken = token(student, "STUDENT");
		String employerToken = token(employer, "EMPLOYER");

		JsonNode createdBatch = body(post(studentToken, "/api/recommendations"), 201);
		assertThat(createdBatch.path("recommendationCount").asInt()).isOne();
		JsonNode recommendationList = body(get(studentToken, "/api/recommendations"), 200);
		long recommendationId = recommendationList.path("recommendations").get(0)
				.path("recommendationId").asLong();
		assertThat(recommendationList.path("refreshRequired").asBoolean()).isFalse();
		assertThat(body(get(studentToken, "/api/recommendations/" + recommendationId), 200)
				.path("jobPostingId").asLong()).isEqualTo(posting);

		String applicationRequest = "{\"recommendationId\":" + recommendationId
				+ ",\"desiredStartDate\":\"" + LocalDate.now().plusDays(1) + "\"}";
		JsonNode createdApplication = body(send("POST", "/api/applications", studentToken, applicationRequest), 201);
		long applicationId = createdApplication.path("applicationId").asLong();
		assertThat(createdApplication.path("status").asString()).isEqualTo("WAITING_RESPONSE");
		assertThat(body(get(studentToken, "/api/applications/me"), 200)
				.path("applications").get(0).path("applicationId").asLong()).isEqualTo(applicationId);

		JsonNode employerApplications = body(get(employerToken,
				"/api/job-postings/" + posting + "/applications"), 200);
		assertThat(employerApplications.path("occupiedCount").asInt()).isOne();
		assertThat(employerApplications.path("applications").get(0).path("applicationId").asLong())
				.isEqualTo(applicationId);
		JsonNode employerApplicationDetail = body(get(employerToken,
				"/api/employer/applications/" + applicationId), 200);
		assertThat(employerApplicationDetail.path("applicant").path("name").asString()).isEqualTo("통합 유학생");
		assertThat(employerApplicationDetail.toString()).doesNotContain("phone", "contact");

		JsonNode createdOffer = body(send("POST", "/api/applications/" + applicationId + "/offers",
				employerToken, null), 201);
		long offerId = createdOffer.path("offerId").asLong();
		assertThat(createdOffer.path("applicationStatus").asString()).isEqualTo("OFFERED");
		assertThat(body(get(studentToken, "/api/applications/" + applicationId), 200)
				.path("offerId").asLong()).isEqualTo(offerId);

		JsonNode accepted = body(send("PATCH", "/api/offers/" + offerId + "/accept", studentToken, null), 200);
		long matchId = accepted.path("employmentMatchId").asLong();
		assertThat(accepted.path("applicationStatus").asString()).isEqualTo("COMPLETED");
		assertThat(body(get(studentToken, "/api/matches/me"), 200)
				.path("matches").get(0).path("matchId").asLong()).isEqualTo(matchId);
		JsonNode studentMatchDetail = body(get(studentToken, "/api/matches/" + matchId), 200);
		assertThat(studentMatchDetail.path("contact").path("name").asString()).isEqualTo("통합 사업주");
		assertThat(studentMatchDetail.path("contact").path("phoneNumber").asString()).isEqualTo("010-2222-2222");

		assertThat(body(get(employerToken, "/api/matches/me"), 200)
				.path("matches").get(0).path("matchId").asLong()).isEqualTo(matchId);
		JsonNode employerMatchDetail = body(get(employerToken, "/api/matches/" + matchId), 200);
		assertThat(employerMatchDetail.path("contact").path("name").asString()).isEqualTo("통합 유학생");
		assertThat(employerMatchDetail.path("contact").path("phoneNumber").asString()).isEqualTo("010-1111-1111");
		assertThat(jdbc.queryForObject("select status from application where id = ?", String.class, applicationId))
				.isEqualTo("COMPLETED");
		assertThat(count("employment_match")).isOne();
	}

	private long createStudent(boolean completePreferences) {
		long user = createUser("STUDENT");
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-01-01', 'VN', '서울', 'INTERMEDIATE')
				""", user);
		if (!completePreferences) {
			return user;
		}
		jdbc.update("insert into student_job_preference(user_id, minimum_hourly_wage, business_type_flexible) "
				+ "values (?, 12000, false)", user);
		jdbc.update("insert into student_preferred_job(user_id, business_type_id) values (?, 1)", user);
		jdbc.update("""
				insert into student_preferred_work_time(user_id, day_of_week, start_time, end_time)
				values (?, 'MON', '09:00', '18:00')
				""", user);
		jdbc.update("""
				insert into student_preferred_location(user_id, location_name, latitude, longitude, radius_m)
				values (?, '테스트 위치', 37.5000000, 127.0000000, 3000)
				""", user);
		for (int criterion = 1; criterion <= 4; criterion++) {
			jdbc.update("""
					insert into student_priority(user_id, criterion_id, priority_order, weight)
					values (?, ?, ?, null)
					""", user, criterion, criterion);
		}
		return user;
	}

	private long createUser(String role) {
		return jdbc.queryForObject("""
				insert into user_account(name, role, email, preferred_language, created_at, updated_at)
				values ('테스트 사용자', ?, ?, 'KO', now(), now()) returning id
				""", Long.class, role, UUID.randomUUID() + "@example.com");
	}

	private void assertRefreshRequiredForListAndDetail(long student, long recommendation) throws Exception {
		JsonNode list = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);
		assertThat(list.path("refreshRequired").asBoolean()).isTrue();
		assertThat(list.path("recommendations").isEmpty()).isTrue();
		assertError(get(token(student, "STUDENT"), "/api/recommendations/" + recommendation),
				409, "RECOMMENDATION_STATE_CONFLICT");
	}

	private void assertRefreshed(long student) throws Exception {
		JsonNode list = body(get(token(student, "STUDENT"), "/api/recommendations"), 200);
		assertThat(list.path("refreshRequired").asBoolean()).isTrue();
		assertThat(body(post(token(student, "STUDENT"), "/api/recommendations/refresh"), 200)
				.path("refreshed").asBoolean()).isTrue();
	}

	private void insertApplicationForNewStudent(long posting, String status) throws Exception {
		long student = createStudent(true);
		body(post(token(student, "STUDENT"), "/api/recommendations"), 201);
		long recommendation = jdbc.queryForObject("""
				select recommendation.id from recommendation
				join recommendation_batch on recommendation_batch.id = recommendation.batch_id
				where recommendation_batch.student_profile_id = ? and recommendation.job_posting_id = ?
				order by recommendation_batch.created_at desc, recommendation_batch.id desc limit 1
				""", Long.class, student, posting);
		insertApplication(student, recommendation, posting, status);
	}

	private void insertApplication(long student, long recommendation, long posting, String status) {
		jdbc.update("""
				insert into application(recommendation_id, student_profile_id, job_posting_id, desired_start_date,
				snapshot_version, application_snapshot, status, employer_response_deadline, applied_at)
				values (?, ?, ?, current_date + 1, 1, '{}'::jsonb, ?, now() + interval '1 day', now())
				""", recommendation, student, posting, status);
	}

	private long createPosting(long employer, String title, int wageAmount, String status) {
		Long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, business_type_id, workplace_address,
				latitude, longitude, business_registration_number, employee_count_range)
				values (?, '테스트 사업장', 1, '서울 테스트로 1', 37.5010000, 127.0010000, ?, '1_4') returning id
				""", Long.class, employer, UUID.randomUUID().toString().substring(0, 20));
		Long posting = jdbc.queryForObject("""
				insert into job_posting(workplace_id, business_type_id, title, wage_type, wage_amount, status,
				application_deadline_date, created_at, updated_at)
				values (?, 1, ?, 'HOURLY', ?, ?, ?, now(), now()) returning id
				""", Long.class, workplace, title, wageAmount, status, LocalDate.now().plusDays(7));
		jdbc.update("""
				insert into job_posting_work_time(job_posting_id, day_of_week, start_time, end_time)
				values (?, 'MON', '10:00', '17:00')
				""", posting);
		return posting;
	}

	private HttpResponse<String> post(String token, String path) throws Exception {
		return client.send(request(token, path).POST(HttpRequest.BodyPublishers.noBody()).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private CompletableFuture<HttpResponse<String>> postAsync(String token, String path) {
		return client.sendAsync(request(token, path).POST(HttpRequest.BodyPublishers.noBody()).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private CompletableFuture<HttpResponse<String>> getAsync(String token, String path) {
		return client.sendAsync(request(token, path).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String token, String path) throws Exception {
		return client.send(request(token, path).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
		HttpRequest.Builder request = request(token, path);
		if (json == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		} else {
			request.header("Content-Type", "application/json")
					.method(method, HttpRequest.BodyPublishers.ofString(json));
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder request(String token, String path) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(15));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return request;
	}

	private JsonNode body(HttpResponse<String> response, int status) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		return mapper.readTree(response.body());
	}

	private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo(code);
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("hirelink").subject(String.valueOf(userId)).issuedAt(now)
				.notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private static String createTestSigningKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}
}
