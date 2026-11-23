package com.hirelink.domain.student;

import com.hirelink.domain.auth.support.LoginSessionFixtures;

import com.hirelink.support.PostgreSQLIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import javax.sql.DataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.hirelink.global.sms.SmsSender;
import static org.awaitility.Awaitility.await;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.hirelink.global.storage.SupabaseStorageClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentProfileUpdateIntegrationTest extends PostgreSQLIntegrationTest {

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
	@MockitoBean
	private SupabaseStorageClient storageClient;
	@Autowired private PasswordEncoder encoder;
	@Autowired private DataSource dataSource;
	@MockitoBean private SmsSender smsSender;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@BeforeEach
	void clearTestDatabase() {
		jdbc.execute("TRUNCATE TABLE student_enrollment_document, student_document_cleanup, employment_popup_notification, employment_match, employment_offer, application, recommendation, recommendation_batch, "
				+ "employer_profile, student_preferred_job, student_preferred_work_time, student_preferred_location, "
				+ "student_job_preference, student_priority, job_posting_work_time, job_posting, workplace, student_career, "
				+ "student_profile, user_term_agreement, phone_verification, "
				+ "phone_verification_send_attempt, refresh_token, auth_account, student_registration_verification, user_account, study_program RESTART IDENTITY");
	}

	@Test
	void migrationRegistersParentVisaTypesAndPreservesTheNineDetailedTypes() {
		Map<String, String> visas = jdbc.queryForList("select visa_code, visa_name from visa_type").stream()
				.collect(Collectors.toMap(row -> (String) row.get("visa_code"), row -> (String) row.get("visa_name")));
		assertThat(visas).hasSize(11)
				.containsAllEntriesOf(Map.of("D-2-1", "전문학사", "D-2-2", "학사", "D-2-3", "석사", "D-2-4", "박사",
						"D-2-6", "교환학생", "D-2-7", "일·학습연계", "D-2-8", "방문학생", "D-4-1", "한국어연수", "D-4-7", "외국어연수"))
				.containsEntry("D-2", "유학").containsEntry("D-4", "일반연수");
		assertThat(jdbc.queryForObject("select count(*) from study_program", Integer.class)).isZero();
	}

	@Test
	void savesAllEditableFieldsAndReadsTheSamePreferredLocationAndCareers() throws Exception {
		long id = createStudent();
		long other = createStudent();
		seedRelated(id);
		seedRelated(other);
		var otherBefore = snapshot(other);
		verifyNewPhone(id);
		var response = patch(token(id, "STUDENT"), fullRequest("서울 용산구"));
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		var body = mapper.readTree(response.body());
		assertThat(body.path("phoneNumber").asString()).isEqualTo("010-9876-5432");
		assertThat(body.path("birthDate").asString()).isEqualTo("2001-05-14");
		assertThat(body.path("koreanLevel").asString()).isEqualTo("ADVANCED");
		assertThat(profile(id)).containsEntry("nationality", "VN").containsEntry("current_region", "서울 용산구");
		assertThat(proof(id)).isEmpty();
		var read = send("GET", "/api/students/me/profile", token(id, "STUDENT"), null);
		assertThat(read.statusCode()).isEqualTo(200);
		var careers = mapper.readTree(read.body()).path("careers");
		assertThat(careers.size()).isEqualTo(1);
		assertThat(careers.get(0).path("careerName").asString()).isEqualTo("새 경력");
		var location = send("GET", "/api/students/me/matching-preferences", token(id, "STUDENT"), null);
		assertThat(location.statusCode()).as(location.body()).isEqualTo(200);
		assertThat(mapper.readTree(location.body()).path("preferredLocation").path("locationName").asString()).isEqualTo("강남역");
		assertThat(snapshot(other)).isEqualTo(otherBefore);
		verifyNoInteractions(storageClient);
	}

	@Test
	void omittedFieldsAndVerifiedDocumentInformationStayUnchanged() throws Exception {
		long id = createStudent();
		seedRelated(id);
		jdbc.update("update student_profile set topik_level=4, grade_level=2, university_name='학교', visa_code='D-2-2', stay_expiration_date='2020-01-01', hirelink_badge_issued_at=now() where id=?", id);
		var before = profile(id);
		var careers = jdbc.queryForList("select * from student_career where user_id=?", id);
		var location = jdbc.queryForMap("select * from student_preferred_location where user_id=?", id);
		assertThat(patch(token(id,"STUDENT"), "{\"currentRegion\":\"서울 용산구\"}").statusCode()).isEqualTo(200);
		before.put("current_region", "서울 용산구");
		assertThat(profile(id)).isEqualTo(before);
		assertThat(jdbc.queryForList("select * from student_career where user_id=?", id)).isEqualTo(careers);
		assertThat(jdbc.queryForMap("select * from student_preferred_location where user_id=?", id)).isEqualTo(location);
		assertThat(patch(token(id,"STUDENT"), "{\"careers\":[]}").statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForList("select * from student_career where user_id=?", id)).isEmpty();
	}

	@Test
	void rejectsUnknownReadOnlyNullAndMalformedInputsAtomically() throws Exception {
		long id = createStudent();
		seedRelated(id);
		var before = snapshot(id);
		for (String field : List.of("name", "nationality", "visaCode", "stayExpirationDate", "topikLevel", "academicState",
				"universityName", "studyProgram", "studyProgramId", "gradeLevel", "userId", "email", "unknown")) {
			assertError(patch(token(id,"STUDENT"), mapper.writeValueAsString(Map.of(field,"x","currentRegion","새 지역"))),400,"INVALID_INPUT");
		}
		for (String field : List.of("birthDate", "phoneNumber", "currentRegion", "koreanLevel", "careers", "preferredLocation")) {
			for (String value : List.of("null", "123", "true")) {
				assertError(patch(token(id,"STUDENT"), "{\""+field+"\":"+value+"}"),400,"INVALID_INPUT");
			}
		}
		for (String json : List.of("{}", "null", "[]", "{", "{\"birthDate\":\"2020-02-30\"}",
				"{\"phoneNumber\":\"01112345678\"}","{\"koreanLevel\":\"EXPERT\"}","{\"currentRegion\":\" \"}",
				"{\"currentRegion\":\""+"가".repeat(151)+"\"}","{\"careers\":[null]}")) {
			assertError(patch(token(id,"STUDENT"),json),400,"INVALID_INPUT");
		}
		assertThat(snapshot(id)).isEqualTo(before);
	}

	@Test
	void validatesAgeUsingKoreanDateAndRollsBackAllChanges() throws Exception {
		long id = createStudent();
		seedRelated(id);
		verifyNewPhone(id);
		var before = snapshot(id);
		var threshold = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(18);
		assertError(patch(token(id,"STUDENT"),fullRequest("새 지역").replace("2001-05-14",threshold.plusDays(1).toString())),400,"INVALID_INPUT");
		assertThat(snapshot(id)).isEqualTo(before);
		assertThat(patch(token(id,"STUDENT"),fullRequest("새 지역").replace("2001-05-14",threshold.toString())).statusCode()).isEqualTo(200);
	}

	@Test
	void invalidCareerOrLocationRollsBackPhoneAndEarlierCareerWrites() throws Exception {
		long id = createStudent();
		seedRelated(id);
		verifyNewPhone(id);
		var before = snapshot(id);
		var request = (tools.jackson.databind.node.ObjectNode) mapper.readTree(fullRequest("새 지역"));
		var second = request.withArray("careers").get(0).deepCopy();
		((tools.jackson.databind.node.ObjectNode) second).put("endMonth","2022-01");
		request.withArray("careers").add(second);
		assertError(patch(token(id,"STUDENT"),request.toString()),400,"INVALID_INPUT");
		assertThat(snapshot(id)).isEqualTo(before);
		for (String field : List.of("radiusM", "latitude", "longitude", "locationName")) {
			var bad = (tools.jackson.databind.node.ObjectNode) mapper.readTree(fullRequest("새 지역"));
			var location = (tools.jackson.databind.node.ObjectNode) bad.path("preferredLocation");
			if (field.equals("locationName")) { location.put(field, " "); } else { location.put(field, 1250); }
			assertError(patch(token(id,"STUDENT"),bad.toString()),400,"INVALID_INPUT");
		}
		assertThat(snapshot(id)).isEqualTo(before);
	}

	@Test
	void careerBatchValidatesRequiredValuesTypesLengthsAndPeriods() throws Exception {
		long id = createStudent();
		seedRelated(id);
		var before = snapshot(id);
		for (String field : List.of("careerName", "startMonth", "endMonth", "description")) {
			for (String value : List.of("null", "123", "true", "\"\"", "\" \"")) {
				var request = careerRequest();
				((tools.jackson.databind.node.ObjectNode) request.path("careers").get(0)).set(field,mapper.readTree(value));
				assertError(patch(token(id,"STUDENT"),request.toString()),400,"INVALID_INPUT");
			}
			var request = careerRequest();
			((tools.jackson.databind.node.ObjectNode) request.path("careers").get(0)).remove(field);
			assertError(patch(token(id,"STUDENT"),request.toString()),400,"INVALID_INPUT");
		}
		for (String month : List.of("2023-00", "2023-13", "2023-3", "2023/03", "2023-03-01", "99999-03")) {
			for (String field : List.of("startMonth", "endMonth")) {
				var request = careerRequest();
				((tools.jackson.databind.node.ObjectNode) request.path("careers").get(0)).put(field,month);
				assertError(patch(token(id,"STUDENT"),request.toString()),400,"INVALID_INPUT");
			}
		}
		for (String field : List.of("careerId", "userId", "unknown")) {
			var request = careerRequest();
			((tools.jackson.databind.node.ObjectNode) request.path("careers").get(0)).put(field, 1);
			assertError(patch(token(id,"STUDENT"),request.toString()),400,"INVALID_INPUT");
		}
		var request = careerRequest();
		var career = (tools.jackson.databind.node.ObjectNode) request.path("careers").get(0);
		career.put("careerName", "가".repeat(151));
		assertError(patch(token(id,"STUDENT"),request.toString()),400,"INVALID_INPUT");
		career.put("careerName", "가".repeat(150));
		String current = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		career.put("startMonth", current).put("endMonth",YearMonth.parse(current).plusMonths(1).toString());
		assertError(patch(token(id,"STUDENT"),request.toString()),400,"INVALID_INPUT");
		assertThat(snapshot(id)).isEqualTo(before);
		career.put("endMonth",current).put("description","음료 제조\n고객 응대");
		assertThat(patch(token(id,"STUDENT"),request.toString()).statusCode()).isEqualTo(200);
	}

	@Test
	void failedDatabaseWritePreservesEveryOriginalRowAndPhoneProofForRetry() throws Exception {
		long id = createStudent();
		seedRelated(id);
		verifyNewPhone(id);
		var before = snapshot(id);
		jdbc.execute("alter table student_profile add constraint test_reject_region check (current_region <> '실패 지역')");
		try {
			assertError(patch(token(id,"STUDENT"),fullRequest("실패 지역")),500,"INTERNAL_SERVER_ERROR");
			assertThat(snapshot(id)).isEqualTo(before);
		} finally { jdbc.execute("alter table student_profile drop constraint test_reject_region"); }
		assertThat(patch(token(id,"STUDENT"),fullRequest("정상 지역")).statusCode()).isEqualTo(200);
	}

	@Test
	void confirmationCreatesFiveMinuteProofWithoutChangingActualAccountAndCannotBeReplayed() throws Exception {
		long id = createStudent();
		var before = jdbc.queryForMap("select * from user_account where id=?", id);
		seedCode(id);
		assertThat(confirm(id).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForMap("select * from user_account where id=?", id)).isEqualTo(before);
		assertThat(jdbc.queryForObject("select extract(epoch from change_expires_at-verified_at) from phone_verification where user_id=?",Integer.class,id)).isEqualTo(300);
		var proof = proof(id);
		assertError(confirm(id),400,"INVALID_VERIFICATION_CODE");
		assertThat(proof(id)).isEqualTo(proof);
		// 인증번호 유효시간이 지나도 확인 성공부터 5분 동안 저장할 수 있다.
		jdbc.update("update phone_verification set expires_at=now()-interval '1 second' where user_id=?",id);
		assertThat(patch(token(id,"STUDENT"), "{\"phoneNumber\":\"010-9876-5432\"}").statusCode()).isEqualTo(200);
		assertThat(proof(id)).isEmpty();
	}

	@Test
	void requiresOwnMatchingUnexpiredProofAndPreservesOtherChanges() throws Exception {
		long id = createStudent();
		seedRelated(id);
		var before = snapshot(id);
		assertError(patch(token(id,"STUDENT"),fullRequest("새 지역")),409,"PHONE_NOT_VERIFIED");
		assertThat(snapshot(id)).isEqualTo(before);
		seedCode(id);
		assertError(patch(token(id,"STUDENT"),fullRequest("새 지역")),409,"PHONE_NOT_VERIFIED");
		assertThat(confirm(id).statusCode()).isEqualTo(200);
		before = snapshot(id);
		assertError(patch(token(id,"STUDENT"),fullRequest("새 지역").replace("010-9876-5432","010-9876-5433")),409,"PHONE_NOT_VERIFIED");
		assertThat(snapshot(id)).isEqualTo(before);
		long other = createStudent();
		assertError(patch(token(other,"STUDENT"),fullRequest("새 지역")),409,"PHONE_NOT_VERIFIED");
		jdbc.update("update phone_verification set verified_at=now()-interval '6 minutes', change_expires_at=now()-interval '1 minute' where user_id=?",id);
		before = snapshot(id);
		assertError(patch(token(id,"STUDENT"),fullRequest("새 지역")),409,"PHONE_NOT_VERIFIED");
		assertThat(snapshot(id)).isEqualTo(before);
	}

	@Test
	void resendingCodeInvalidatesConfirmedProof() throws Exception {
		long id = createStudent();
		verifyNewPhone(id);
		var response = send("POST","/api/auth/phone-verifications",token(id,"STUDENT"),"{\"phoneNumber\":\"01098765432\"}");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		assertThat(proof(id).get(0)).containsEntry("verified_at",null).containsEntry("change_expires_at",null);
		assertError(patch(token(id,"STUDENT"),fullRequest("새 지역")),409,"PHONE_NOT_VERIFIED");
	}

	@Test
	void duplicatePhoneAtFinalSaveRollsBackAndConcurrentSavesHaveOneWinner() throws Exception {
		long first = createStudent();
		long second = createStudent();
		verifyNewPhone(first);
		verifyNewPhone(second);
		var one = client.sendAsync(request(token(first,"STUDENT"),fullRequest("첫 지역")),HttpResponse.BodyHandlers.ofString());
		var two = client.sendAsync(request(token(second,"STUDENT"),fullRequest("둘 지역")),HttpResponse.BodyHandlers.ofString());
		var r1 = one.get(20,TimeUnit.SECONDS);
		var r2 = two.get(20,TimeUnit.SECONDS);
		assertThat(List.of(r1.statusCode(),r2.statusCode())).containsExactlyInAnyOrder(200,409);
		long loser = r1.statusCode()==409 ? first : second;
		assertError(r1.statusCode()==409?r1:r2,409,"DUPLICATE_PHONE_NUMBER");
		assertThat(profile(loser).get("current_region")).isEqualTo("서울 마포구");
		assertThat(proof(loser)).hasSize(1);
		assertThat(jdbc.queryForObject("select count(*) from user_account where phone_number='01098765432'",Integer.class)).isEqualTo(1);
	}

	@Test
	void samePhoneDoesNotRequireNewProofAndInitialAccountConfirmationStillSavesImmediately() throws Exception {
		long id = createStudent();
		String phone = jdbc.queryForObject("select phone_number from user_account where id=?",String.class,id);
		assertThat(patch(token(id,"STUDENT"),mapper.writeValueAsString(Map.of("phoneNumber",phone,"currentRegion","새 지역"))).statusCode()).isEqualTo(200);
		jdbc.update("delete from student_profile where id=?",id);
		seedCode(id);
		assertThat(confirm(id).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select phone_number from user_account where id=?",String.class,id)).isEqualTo("01098765432");
		assertThat(proof(id)).isEmpty();
	}

	@Test
	void finalSaveChecksExpiryAfterWaitingForMemberLock() throws Exception {
		long id = createStudent();
		verifyNewPhone(id);
		String accessToken = token(id,"STUDENT");
		CompletableFuture<HttpResponse<String>> pending;
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("select id from user_account where id=? for update")) {
				statement.setLong(1,id); statement.executeQuery().close();
			}
			try {
				pending = client.sendAsync(request(accessToken,fullRequest("새 지역")),HttpResponse.BodyHandlers.ofString());
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%user_account%'",Integer.class)).isPositive());
				jdbc.update("update phone_verification set verified_at=now()-interval '6 minutes',change_expires_at=now()-interval '1 minute' where user_id=?",id);
			} finally { connection.rollback(); }
		}
		assertError(pending.get(15,TimeUnit.SECONDS),409,"PHONE_NOT_VERIFIED");
		assertThat(profile(id).get("current_region")).isEqualTo("서울 마포구");
	}

	@Test
	void simultaneousPartialUpdatesPreserveBothChanges() throws Exception {
		long id = createStudent();
		String token = token(id, "STUDENT");
		var region = client.sendAsync(request(token, "{\"currentRegion\":\"서울 용산구\"}"),HttpResponse.BodyHandlers.ofString());
		var korean = client.sendAsync(request(token, "{\"koreanLevel\":\"ADVANCED\"}"),HttpResponse.BodyHandlers.ofString());
		assertThat(region.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(korean.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		assertThat(profile(id)).containsEntry("current_region", "서울 용산구").containsEntry("korean_level", "ADVANCED");
	}

	@Test
	void requiresCurrentActiveStudentAndOwnExistingProfile() throws Exception {
		String json = "{\"currentRegion\":\"새 지역\"}";
		assertError(patch(null,json),401,"UNAUTHORIZED");
		assertError(patch("invalid",json),401,"UNAUTHORIZED");
		assertError(patch(token(999,"STUDENT"),json),401,"UNAUTHORIZED");
		long id = createStudent();
		jdbc.update("update user_account set role='EMPLOYER' where id=?",id);
		assertError(patch(token(id,"STUDENT"),json),403,"FORBIDDEN");
		jdbc.update("update user_account set role='STUDENT',withdrawn_at=now() where id=?",id);
		assertError(patch(token(id,"STUDENT"),json),403,"WITHDRAWN_ACCOUNT");
		jdbc.update("update user_account set withdrawn_at=null where id=?",id);
		jdbc.update("delete from student_profile where id=?",id);
		assertError(patch(token(id,"STUDENT"),json),404,"STUDENT_PROFILE_NOT_FOUND");
	}

	@Test
	void legacyCareerWritesNeverPersistDrafts() throws Exception {
		long id = createStudent();
		seedRelated(id);
		var before = snapshot(id);
		for (String method : List.of("POST","PATCH","DELETE")) {
			String path = "/api/students/me/careers"+(method.equals("POST")?"":"/1");
			assertError(send(method,path,token(id,"STUDENT"),"{}"),400,"INVALID_INPUT");
			assertError(send(method,path,null,"{}"),401,"UNAUTHORIZED");
		}
		assertThat(snapshot(id)).isEqualTo(before);
	}

	private tools.jackson.databind.node.ObjectNode careerRequest() {
		var request = (tools.jackson.databind.node.ObjectNode) mapper.readTree(fullRequest("새 지역"));
		request.remove("phoneNumber");
		return request;
	}

	private String fullRequest(String region) {
		return """
			{"birthDate":"2001-05-14","phoneNumber":"010-9876-5432","currentRegion":"%s","koreanLevel":"ADVANCED",
			"preferredLocation":{"locationName":"강남역","latitude":37.4979,"longitude":127.0276,"radiusM":500},
			"careers":[{"careerName":"새 경력","startMonth":"2023-03","endMonth":"2023-09","description":"음료 제조"}]}
			""".formatted(region);
	}

	private void seedRelated(long id) {
		jdbc.update("insert into student_career(user_id,career_name,start_month,end_month,description) values (?,'기존 경력','2022-01','2022-06','판매')",id);
		jdbc.update("insert into student_preferred_location(user_id,location_name,latitude,longitude,radius_m) values (?,'기존 위치',37,127,300)",id);
	}

	private void seedCode(long id) {
		jdbc.update("insert into phone_verification(user_id,phone_number,code_hash,expires_at) values (?,'01098765432',?,now()+interval '5 minutes')",id,encoder.encode("123456"));
	}

	private void verifyNewPhone(long id) throws Exception {
		seedCode(id);
		var response = confirm(id);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
	}

	private HttpResponse<String> confirm(long id) throws Exception {
		return send("POST","/api/auth/phone-verifications/verify",token(id,"STUDENT"),"{\"phoneNumber\":\"010-9876-5432\",\"code\":\"123456\"}");
	}

	private List<Map<String,Object>> proof(long id) {
		return jdbc.queryForList("select * from phone_verification where user_id=?",id);
	}

	private List<Object> snapshot(long id) {
		return List.of(jdbc.queryForMap("select * from user_account where id=?",id),profile(id),
			jdbc.queryForList("select * from student_career where user_id=? order by id",id),
			jdbc.queryForList("select * from student_preferred_location where user_id=?",id),proof(id));
	}

	private HttpResponse<String> send(String method,String path,String token,String json) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(15));
		if (token!=null) { request.header("Authorization","Bearer "+token); }
		request.header("Content-Type","application/json").method(method,json==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json));
		return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
	}

	private long createStudent() {
		long id = jdbc.queryForObject("insert into user_account(email, role, name) values (?, 'STUDENT', '테스트 이름') returning id",
				Long.class, UUID.randomUUID() + "@example.com");
		jdbc.update("update user_account set phone_number = ?, phone_verified_at = now() where id = ?",
				String.format(java.util.Locale.ROOT, "010%08d", id), id);
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level)
				values (?, '2000-03-14', 'VN', '서울 마포구', 'INTERMEDIATE')
				""", id);
		return id;
	}

	private Map<String, Object> profile(long id) {
		return jdbc.queryForMap("select * from student_profile where id = ?", id);
	}

	private String token(long userId, String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().claim("sid", LoginSessionFixtures.create(jdbc, userId)).issuer("hirelink").subject(Long.toString(userId))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private HttpRequest request(String token, String json) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl())).timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers.ofString(json));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return request.build();
	}

	private HttpResponse<String> patch(String token, String json) throws Exception {
		return client.send(request(token, json), HttpResponse.BodyHandlers.ofString());
	}

	private String baseUrl() {
		return "http://localhost:" + port + "/api/students/me/profile";
	}

	private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
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
