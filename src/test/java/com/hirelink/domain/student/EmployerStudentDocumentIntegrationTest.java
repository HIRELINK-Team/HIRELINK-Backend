package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.hirelink.domain.auth.support.LoginSessionFixtures;
import com.hirelink.domain.student.client.AcademicOcrClient;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.dto.AcademicExtraction;
import com.hirelink.domain.student.dto.StudentDocumentContent;
import com.hirelink.global.exception.StudentDocumentException;
import com.hirelink.support.PostgreSQLIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"jwt.secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkwMTI=",
		"student-document.cleanup-initial-delay=3600000",
		"student-working-conditions.refresh-enabled=false"})
class EmployerStudentDocumentIntegrationTest extends PostgreSQLIntegrationTest {
	private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired JwtEncoder encoder;
	@MockitoBean StudentDocumentStorageClient storage;
	@MockitoBean AcademicOcrClient ocr;
	@MockitoBean Clock clock;
	@LocalServerPort int port;
	private final HttpClient client = HttpClient.newHttpClient();
	private final Map<String, byte[]> files = new ConcurrentHashMap<>();
	private long employer;
	private long student;
	private long match;

	@BeforeEach void setup() {
		jdbc.execute("TRUNCATE TABLE user_account, social_auth_session, student_document_cleanup RESTART IDENTITY CASCADE");
		reset(storage, ocr, clock);
		files.clear();
		when(clock.instant()).thenReturn(NOW);
		when(clock.getZone()).thenReturn(ZoneOffset.UTC);
		when(storage.download(anyString())).thenAnswer(call -> new StudentDocumentContent(files.get(call.getArgument(0)), MediaType.APPLICATION_PDF));
		doAnswer(call -> { files.put(call.getArgument(0), ((byte[]) call.getArgument(1)).clone()); return null; })
				.when(storage).upload(anyString(), any(), anyString());
		doAnswer(call -> { files.remove(call.getArgument(0)); return null; }).when(storage).delete(anyString());
		when(ocr.recognize(eq("TOPIK"), any(), anyString())).thenReturn(new AcademicExtraction(null, null, null, (short)5));
		employer = user("EMPLOYER");
		student = user("STUDENT");
		jdbc.update("INSERT INTO student_profile(id,birth_date,nationality,current_region,korean_level,onboarding_completed_at) VALUES (?,'2000-01-01','VN','서울','INTERMEDIATE',now())", student);
		match = matched(employer);
	}

	@ParameterizedTest @ValueSource(strings = {"enrollment", "transcript", "topik"})
	void ownerReceivesFinalOriginalWithoutChangingRecruitmentOrDocument(String kind) throws Exception {
		String key = document(kind);
		var before = jdbc.queryForList("SELECT * FROM student_enrollment_document WHERE user_id=?", student);
		var matchBefore = jdbc.queryForMap("SELECT * FROM employment_match WHERE id=?", match);
		var offersBefore = jdbc.queryForList("SELECT * FROM employment_offer ORDER BY id");
		var applicationsBefore = jdbc.queryForList("SELECT * FROM application ORDER BY id");
		var response = get(employer, match, kind);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(files.get(key));
		assertThat(response.headers().firstValue("Content-Type")).contains("application/pdf");
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
		assertThat(response.headers().firstValue("Content-Disposition")).contains("inline; filename=\"" + kind + ".pdf\"");
		assertThat(jdbc.queryForList("SELECT * FROM student_enrollment_document WHERE user_id=?", student)).isEqualTo(before);
		assertThat(jdbc.queryForMap("SELECT * FROM employment_match WHERE id=?", match)).isEqualTo(matchBefore);
		assertThat(jdbc.queryForList("SELECT * FROM employment_offer ORDER BY id")).isEqualTo(offersBefore);
		assertThat(jdbc.queryForList("SELECT * FROM application ORDER BY id")).isEqualTo(applicationsBefore);
		verify(storage).download(key);
		verifyNoMoreInteractions(storage);
	}

	@Test void rejectsUnauthenticatedWrongRoleOtherEmployerAndUnsupportedKind() throws Exception {
		document("topik");
		assertThat(get(0, match, "topik").statusCode()).isEqualTo(401);
		assertError(get(student, match, "topik"), 403, "FORBIDDEN");
		assertError(get(user("EMPLOYER"), match, "topik"), 403, "FORBIDDEN");
		assertError(get(employer, match, "registration"), 400, "INVALID_DOCUMENT_TYPE");
		assertError(get(employer, match, "TOPIK"), 400, "INVALID_DOCUMENT_TYPE");
		assertError(get(employer, 0, "topik"), 400, "INVALID_INPUT");
		jdbc.update("UPDATE user_account SET withdrawn_at=now() WHERE id=?", employer);
		assertThat(get(employer, match, "topik").statusCode()).isEqualTo(403);
		verifyNoInteractions(storage);
	}

	@Test void deletedMatchCannotBorrowPermissionFromAnotherActiveMatch() throws Exception {
		String key = document("enrollment");
		long remaining = matched(employer);
		long otherEmployerMatch = matched(user("EMPLOYER"));
		jdbc.update("UPDATE employment_match SET deleted_at=now() WHERE id=?", match);
		assertError(get(employer, match, "enrollment"), 404, "EMPLOYMENT_MATCH_NOT_FOUND");
		assertError(get(employer, otherEmployerMatch, "enrollment"), 403, "FORBIDDEN");
		assertThat(get(employer, remaining, "enrollment").body()).isEqualTo(files.get(key));
		jdbc.update("UPDATE employment_match SET deleted_at=now() WHERE id=?", remaining);
		assertError(get(employer, remaining, "enrollment"), 404, "EMPLOYMENT_MATCH_NOT_FOUND");
		assertThat(files).containsKey(key);
		verify(storage).download(key);
		verifyNoMoreInteractions(storage);
	}

	@Test void applicationAndPendingOfferDoNotGrantAccessWithoutCompletedMatch() throws Exception {
		document("topik");
		jdbc.update("DELETE FROM employment_match WHERE id=?", match);
		jdbc.update("UPDATE employment_offer SET status='PENDING',responded_at=null");
		jdbc.update("UPDATE application SET status='OFFERED'");
		assertError(get(employer, match, "topik"), 404, "EMPLOYMENT_MATCH_NOT_FOUND");
		jdbc.update("DELETE FROM employment_offer");
		jdbc.update("UPDATE application SET status='WAITING_RESPONSE'");
		assertError(get(employer, match, "topik"), 404, "EMPLOYMENT_MATCH_NOT_FOUND");
		verifyNoInteractions(storage);
	}

	@Test void draftOnlyAndArchivedDocumentsAreNotExposed() throws Exception {
		assertError(get(employer, match, "topik"), 404, "DOCUMENT_NOT_REGISTERED");
		jdbc.update("INSERT INTO student_enrollment_document(user_id,document_type,draft_id,draft_object_key,draft_topik_level) VALUES (?,'TOPIK',?, ?,4)", student, UUID.randomUUID(), "topik/"+student+"/"+UUID.randomUUID());
		assertError(get(employer, match, "topik"), 404, "DOCUMENT_NOT_REGISTERED");
		jdbc.update("DELETE FROM student_enrollment_document WHERE user_id=?", student);
		document("topik");
		jdbc.update("UPDATE student_enrollment_document SET deleted_at=now(),object_key=null WHERE user_id=?", student);
		assertError(get(employer, match, "topik"), 404, "DOCUMENT_NOT_REGISTERED");
		verifyNoInteractions(storage);
	}

	@Test void previewDoesNotPublishNewOriginalUntilConfirmationSucceeds() throws Exception {
		String old = document("topik");
		String other = document("transcript");
		String multipart = "--doc-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-new\r\n--doc-boundary--\r\n";
		var uploaded = client.send(HttpRequest.newBuilder(uri("/api/students/me/documents/topik/preview"))
				.header("Authorization", "Bearer " + token(student)).header("Content-Type", "multipart/form-data; boundary=doc-boundary")
				.POST(HttpRequest.BodyPublishers.ofString(multipart)).build(), HttpResponse.BodyHandlers.ofByteArray());
		assertThat(uploaded.statusCode()).as(new String(uploaded.body(), StandardCharsets.UTF_8)).isEqualTo(200);
		String draft = mapper.readTree(uploaded.body()).path("draftId").asString();
		assertThat(get(employer, match, "topik").body()).isEqualTo(files.get(old));
		var confirmed = client.send(HttpRequest.newBuilder(uri("/api/students/me/documents/topik/" + draft + "/confirm"))
				.header("Authorization", "Bearer " + token(student)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
		assertThat(confirmed.statusCode()).as(new String(confirmed.body(), StandardCharsets.UTF_8)).isEqualTo(200);
		assertThat(new String(get(employer, match, "topik").body(), StandardCharsets.UTF_8)).isEqualTo("%PDF-new");
		assertThat(get(employer, match, "transcript").body()).isEqualTo(files.get(other));
		verify(storage).download("topik/" + student + "/" + draft);
	}

	@Test void dormancyIsAllowedOnlyBeforeExactThirtyDayDeadlineEvenIfCleanupIsDelayed() throws Exception {
		String key = document("topik");
		Timestamp requested = Timestamp.from(NOW.minusSeconds(30L * 86400));
		jdbc.update("UPDATE user_account SET withdrawn_at=?,withdrawal_requested_at=? WHERE id=?", requested, requested, student);
		when(clock.instant()).thenReturn(NOW.minusNanos(1000));
		assertThat(get(employer, match, "topik").statusCode()).isEqualTo(200);
		when(clock.instant()).thenReturn(NOW);
		assertError(get(employer, match, "topik"), 403, "FORBIDDEN");
		when(clock.instant()).thenReturn(NOW.plusSeconds(1));
		assertError(get(employer, match, "topik"), 403, "FORBIDDEN");
		assertThat(files).containsKey(key);
		assertThat(jdbc.queryForObject("SELECT object_key FROM student_enrollment_document WHERE user_id=?", String.class, student)).isEqualTo(key);
		jdbc.update("UPDATE user_account SET withdrawn_at=null,withdrawal_requested_at=null WHERE id=?", student);
		assertThat(get(employer, match, "topik").statusCode()).isEqualTo(200);
	}

	@Test void finalizedAndLegacyWithdrawnAccountsCannotExposeRetainedData() throws Exception {
		document("topik");
		jdbc.update("UPDATE user_account SET withdrawn_at=now() WHERE id=?", student);
		assertError(get(employer, match, "topik"), 403, "FORBIDDEN");
		jdbc.update("UPDATE user_account SET withdrawal_requested_at=now(),withdrawal_finalized_at=now(),withdrawal_retain_until=now()+interval '3 years' WHERE id=?", student);
		assertError(get(employer, match, "topik"), 403, "FORBIDDEN");
		verifyNoInteractions(storage);
	}

	@Test void storageFailureDoesNotLeakLocationOrChangeRegisteredDocument() throws Exception {
		String key = document("topik");
		var before = jdbc.queryForList("SELECT * FROM student_enrollment_document WHERE user_id=?", student);
		when(storage.download(key)).thenThrow(new StudentDocumentException(HttpStatus.BAD_GATEWAY, "STUDENT_DOCUMENT_STORAGE_FAILED", "서류 저장소를 이용할 수 없습니다."));
		var response = get(employer, match, "topik");
		assertError(response, 502, "STUDENT_DOCUMENT_STORAGE_FAILED");
		assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain(key);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(jdbc.queryForList("SELECT * FROM student_enrollment_document WHERE user_id=?", student)).isEqualTo(before);
	}

	@Test void refusesStoredObjectKeysForAnotherStudentOrDocumentKind() throws Exception {
		document("topik");
		for (String key : new String[]{"topik/999/"+UUID.randomUUID(), "enrollment/"+student+"/"+UUID.randomUUID()}) {
			jdbc.update("UPDATE student_enrollment_document SET object_key=? WHERE user_id=?", key, student);
			assertError(get(employer, match, "topik"), 502, "STUDENT_DOCUMENT_STORAGE_FAILED");
		}
		verifyNoInteractions(storage);
	}

	@Test void openApiDescribesBinarySuccessAndJsonErrors() throws Exception {
		var response = client.send(HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		var operation = mapper.readTree(response.body()).path("paths").path("/api/employers/me/matches/{matchId}/student-documents/{kind}").path("get");
		for (String type : new String[]{"application/pdf", "image/jpeg", "image/png"}) {
			assertThat(operation.path("responses").path("200").path("content").path(type).path("schema").path("format").asString()).isEqualTo("binary");
		}
		assertThat(operation.path("responses").path("403").path("content").has("application/json")).isTrue();
		assertThat(operation.has("requestBody")).isFalse();
	}

	private long user(String role) {
		return jdbc.queryForObject("INSERT INTO user_account(role,email,name) VALUES (?,?,'테스트') RETURNING id", Long.class, role, UUID.randomUUID()+"@example.com");
	}
	private long matched(long owner) {
		long workplace = jdbc.queryForObject("INSERT INTO workplace(employer_user_id,workplace_name,representative_name,business_registration_file_key,business_registration_number,business_type_id,workplace_address,latitude,longitude,employee_count_range) VALUES (?,'사업장','대표','test/cert.pdf',?,1,'서울',37.12,127.12,'UNDER_5') RETURNING id", Long.class, owner, String.format("%010d", owner*1000 + jdbc.queryForObject("SELECT count(*) FROM workplace", Long.class)));
		long posting = jdbc.queryForObject("INSERT INTO job_posting(workplace_id,title,wage_type,wage_amount,status) VALUES (?,'공고','HOURLY',12500,'CLOSED') RETURNING id", Long.class, workplace);
		long batch = jdbc.queryForObject("INSERT INTO recommendation_batch(student_profile_id,algorithm_version,input_snapshot,created_at) VALUES (?,'v1','{}'::jsonb,now()) RETURNING id", Long.class, student);
		long recommendation = jdbc.queryForObject("INSERT INTO recommendation(batch_id,job_posting_id,rank,score,reason_json,created_at) VALUES (?,?,1,90.0,'{}'::jsonb,now()) RETURNING id", Long.class, batch, posting);
		long application = jdbc.queryForObject("INSERT INTO application(recommendation_id,student_profile_id,job_posting_id,desired_start_date,snapshot_version,application_snapshot,status,employer_response_deadline,applied_at) VALUES (?,?,?,'2030-01-02',1,'{}'::jsonb,'COMPLETED',now(),now()) RETURNING id", Long.class, recommendation, student, posting);
		long offer = jdbc.queryForObject("INSERT INTO employment_offer(application_id,status,student_response_deadline,offered_at,responded_at) VALUES (?,'ACCEPTED',now(),now(),now()) RETURNING id", Long.class, application);
		return jdbc.queryForObject("INSERT INTO employment_match(employment_offer_id,completed_at) VALUES (?,now()) RETURNING id", Long.class, offer);
	}
	private String document(String kind) {
		String key = kind + "/" + student + "/" + UUID.randomUUID();
		jdbc.update("INSERT INTO student_enrollment_document(user_id,document_type,object_key,registered_at,university_name,grade_level,average_gpa,academic_year,semester,topik_level) VALUES (?,?,?,now(),'학교',2,4.24,2026,'SECOND',4)", student, kind.toUpperCase(Locale.ROOT), key);
		files.put(key, ("%PDF-" + kind).getBytes(StandardCharsets.UTF_8));
		return key;
	}
	private HttpResponse<byte[]> get(long viewer, long matchId, String kind) throws Exception {
		var request = HttpRequest.newBuilder(uri("/api/employers/me/matches/"+matchId+"/student-documents/"+kind)).GET();
		if (viewer != 0) { request.header("Authorization", "Bearer " + token(viewer)); }
		return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
	}
	private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
	private String token(long id) {
		Instant now = Instant.now();
		String role = jdbc.queryForObject("SELECT role FROM user_account WHERE id=?", String.class, id);
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), JwtClaimsSet.builder()
				.claim("sid", LoginSessionFixtures.create(jdbc, id)).issuer("hirelink").subject(Long.toString(id))
				.issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600)).id(UUID.randomUUID().toString())
				.claim("role", role).claim("token_type", "access").build())).getTokenValue();
	}
	private void assertError(HttpResponse<byte[]> response, int status, String code) {
		assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(status);
		var body = mapper.readTree(response.body());
		assertThat(body.path("code").asString()).isEqualTo(code);
		assertThat(body.size()).isEqualTo(2);
	}
}
