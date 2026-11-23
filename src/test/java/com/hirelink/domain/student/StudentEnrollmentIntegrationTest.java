package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.hirelink.domain.student.client.EnrollmentOcrClient;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.dto.EnrollmentExtraction;
import com.hirelink.domain.student.service.StudentDocumentCleanupService;
import com.hirelink.global.exception.StudentDocumentException;
import com.hirelink.support.PostgreSQLIntegrationTest;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"jwt.secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkwMTI=",
		"student-document.cleanup-initial-delay=3600000"})
class StudentEnrollmentIntegrationTest extends PostgreSQLIntegrationTest {
	private static final String PATH = "/api/students/me/documents/enrollment";
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired JwtEncoder encoder;
	@Autowired StudentDocumentCleanupService cleaner;
	@MockitoBean EnrollmentOcrClient ocr;
	@MockitoBean StudentDocumentStorageClient storage;
	@LocalServerPort int port;
	private final HttpClient client = HttpClient.newHttpClient();
	private final Map<String, byte[]> files = new ConcurrentHashMap<>();
	private long user;

	@BeforeEach void setup() {
		jdbc.execute("TRUNCATE TABLE user_account, student_document_cleanup RESTART IDENTITY CASCADE");
		reset(ocr, storage); files.clear();
		when(ocr.recognize(any(), anyString())).thenReturn(new EnrollmentExtraction("숭실대학교", "서울", null, 3));
		doAnswer(call -> { files.put(call.getArgument(0), ((byte[]) call.getArgument(1)).clone()); return null; }).when(storage).upload(anyString(), any(), anyString());
		doAnswer(call -> { files.remove(call.getArgument(0)); return null; }).when(storage).delete(anyString());
		user = student("STUDENT");
	}

	@Test void previewDoesNotPublishAndConfirmationAtomicallyReplacesOriginalAndExtraction() throws Exception {
		UUID first = preview(user);
		assertThat(jdbc.queryForObject("select grade_level from student_profile where id=?", Short.class,user)).isEqualTo((short) 1);
		assertError(send("GET", PATH, user),404,"ENROLLMENT_NOT_REGISTERED");
		String oldKey = draftKey();
		var confirmed = confirm(user, first);
		assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
		assertThat(mapper.readTree(confirmed.body()).path("gradeLevel").asInt()).isEqualTo(2);
		assertThat(mapper.readTree(confirmed.body()).path("campusName").asString()).isEqualTo("서울");
		assertThat(files.get(oldKey)).isEqualTo("%PDF-test".getBytes(StandardCharsets.UTF_8));
		when(ocr.recognize(any(), anyString())).thenReturn(new EnrollmentExtraction("다른대학교", null, 4, null));
		UUID second = preview(user);
		assertThat(mapper.readTree(send("GET", PATH, user).body()).path("universityName").asString()).isEqualTo("숭실대학교");
		assertThat(files).containsKey(oldKey);
		assertThat(confirm(user, second).statusCode()).isEqualTo(200);
		assertThat(files).doesNotContainKey(oldKey).hasSize(1);
		assertThat(jdbc.queryForMap("select university_name,grade_level from student_profile where id=?",user))
				.containsEntry("university_name","다른대학교").containsEntry("grade_level",4);
		assertError(confirm(user, second),409,"ENROLLMENT_DRAFT_NOT_FOUND");
	}

	@Test void outOfRangeUnreadableAndStorageFailuresPreserveFinalOriginalAndValues() throws Exception {
		assertThat(confirm(user, preview(user)).statusCode()).isEqualTo(200);
		var before = snapshot(); String original = currentKey();
		for (var result : new EnrollmentExtraction[] {new EnrollmentExtraction("학교",null,5,null), new EnrollmentExtraction("학교",null,null,9)}) {
			when(ocr.recognize(any(),anyString())).thenReturn(result);
			var response = upload(user,"%PDF-other","application/pdf");
			assertError(response,422,"UNSUPPORTED_GRADE_LEVEL");
			assertThat(mapper.readTree(response.body()).path("message").asString()).isEqualTo("지원하는 학년 범위를 벗어났습니다");
			assertThat(snapshot()).isEqualTo(before); assertThat(files).containsKey(original).hasSize(1);
		}
		when(ocr.recognize(any(),anyString())).thenThrow(EnrollmentExtraction.unreadable());
		assertError(upload(user,"%PDF-bad","application/pdf"),422,"UNREADABLE_ENROLLMENT");
		assertThat(snapshot()).isEqualTo(before);
		doReturn(new EnrollmentExtraction("학교",null,2,null)).when(ocr).recognize(any(),anyString());
		doThrow(new StudentDocumentException(HttpStatus.BAD_GATEWAY,"STUDENT_DOCUMENT_STORAGE_FAILED","저장 실패")).when(storage).upload(anyString(),any(),anyString());
		assertError(upload(user,"%PDF-bad","application/pdf"),502,"STUDENT_DOCUMENT_STORAGE_FAILED");
		assertThat(snapshot()).isEqualTo(before); assertThat(files).containsKey(original).hasSize(1);
	}

	@Test void missingFieldsAreExplainedAtUploadAndConfirmationWithoutReplacingFinalData() throws Exception {
		assertThat(confirm(user, preview(user)).statusCode()).isEqualTo(200);
		var before = snapshot(); String original = currentKey();
		var cases = Map.of(
				new EnrollmentExtraction(null,null,2,null), "대학교명을",
				new EnrollmentExtraction("학교",null,null,null), "학년 또는 학기 수를",
				new EnrollmentExtraction(null,null,null,null), "대학교명과 학년 또는 학기 수를");
		clearInvocations(storage);
		for (var entry : cases.entrySet()) {
			when(ocr.recognize(any(),anyString())).thenReturn(entry.getKey());
			var response = upload(user,"%PDF-new","application/pdf");
			assertError(response,422,"UNREADABLE_ENROLLMENT");
			assertThat(mapper.readTree(response.body()).path("message").asString()).startsWith(entry.getValue()+" 확인하지 못했습니다.");
			assertThat(snapshot()).isEqualTo(before); assertThat(files).containsOnlyKeys(original);
		}
		verifyNoInteractions(storage);
		when(ocr.recognize(any(),anyString())).thenReturn(new EnrollmentExtraction("새학교",null,3,null));
		UUID draft = preview(user);
		jdbc.update("update student_enrollment_document set draft_university_name=null,draft_grade_level=null,draft_semester_count=null where user_id=?",user);
		before = snapshot(); var keys = Set.copyOf(files.keySet());
		var response = confirm(user,draft);
		assertError(response,422,"UNREADABLE_ENROLLMENT");
		assertThat(mapper.readTree(response.body()).path("message").asString()).startsWith("대학교명과 학년 또는 학기 수를 확인하지 못했습니다.");
		assertThat(snapshot()).isEqualTo(before); assertThat(files.keySet()).isEqualTo(keys);
	}

	@Test void finalConfirmationRevalidatesSavedExtractionAndRollsBackOnDatabaseFailure() throws Exception {
		assertThat(confirm(user, preview(user)).statusCode()).isEqualTo(200);
		UUID draft = preview(user);
		jdbc.update("update student_enrollment_document set draft_grade_level=5 where user_id=?",user);
		var before = snapshot(); String original = currentKey();
		assertError(confirm(user,draft),422,"UNSUPPORTED_GRADE_LEVEL");
		assertThat(snapshot()).isEqualTo(before); assertThat(files).containsKey(original);
		jdbc.update("update student_enrollment_document set draft_grade_level=3 where user_id=?",user);
		before = snapshot();
		jdbc.execute("CREATE FUNCTION fail_enrollment_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
		jdbc.execute("CREATE TRIGGER fail_enrollment_update BEFORE UPDATE ON student_profile FOR EACH ROW EXECUTE FUNCTION fail_enrollment_update()");
		try {
			assertError(confirm(user,draft),500,"INTERNAL_SERVER_ERROR");
			assertThat(snapshot()).isEqualTo(before); assertThat(files).containsKey(original);
		} finally {
			jdbc.execute("DROP TRIGGER fail_enrollment_update ON student_profile"); jdbc.execute("DROP FUNCTION fail_enrollment_update()");
		}
		assertThat(confirm(user,draft).statusCode()).isEqualTo(200);
	}

	@Test void rejectsOthersOldDraftsWithdrawnAccountsAndInvalidUploads() throws Exception {
		UUID first = preview(user); long other = student("STUDENT");
		assertError(confirm(other,first),409,"ENROLLMENT_DRAFT_NOT_FOUND");
		String old = draftKey(); UUID latest = preview(user);
		assertThat(files).doesNotContainKey(old);
		assertError(confirm(user,first),409,"ENROLLMENT_DRAFT_NOT_FOUND");
		assertError(upload(user,"not a pdf","application/pdf"),415,"UNSUPPORTED_FILE_TYPE");
		assertError(upload(user,"%PDF-test","text/plain"),415,"UNSUPPORTED_FILE_TYPE");
		assertError(upload(user,"%PDF-"+"x".repeat(5*1024*1024),"application/pdf"),413,"FILE_TOO_LARGE");
		assertError(send("GET",PATH,0),401,"UNAUTHORIZED");
		long employer=student("EMPLOYER");
		assertError(confirm(employer,latest),403,"FORBIDDEN");
		jdbc.update("update user_account set withdrawn_at=now() where id=?",user);
		assertError(confirm(user,latest),403,"WITHDRAWN_ACCOUNT");
	}

	@Test void uploadDatabaseFailureLeavesFinalDataAndQueuesUnlinkedFileForCleanup() throws Exception {
		assertThat(confirm(user,preview(user)).statusCode()).isEqualTo(200);
		var before=snapshot(); String original=currentKey();
		jdbc.execute("CREATE FUNCTION fail_enrollment_stage() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test stage rollback'; END $$");
		jdbc.execute("CREATE TRIGGER fail_enrollment_stage BEFORE UPDATE ON student_enrollment_document FOR EACH ROW EXECUTE FUNCTION fail_enrollment_stage()");
		try {
			assertError(upload(user,"%PDF-new","application/pdf"),500,"INTERNAL_SERVER_ERROR");
			assertThat(snapshot()).isEqualTo(before); assertThat(files).containsKey(original).hasSize(2);
		} finally {
			jdbc.execute("DROP TRIGGER fail_enrollment_stage ON student_enrollment_document"); jdbc.execute("DROP FUNCTION fail_enrollment_stage()");
		}
		String unlinked=jdbc.queryForObject("select object_key from student_document_cleanup",String.class);
		assertThat(unlinked).isNotEqualTo(original);
		jdbc.update("update student_document_cleanup set available_at=now()-interval '1 second'");
		cleaner.deleteDue(unlinked);
		assertThat(files).containsOnlyKeys(original);
	}

	@Test void concurrentConfirmOnlyCommitsOnceAndDeleteFailureIsRetriedWithoutUndoingRegistration() throws Exception {
		UUID draft = preview(user);
		var request = request("POST",PATH+"/"+draft+"/confirm",user);
		var one=client.sendAsync(request,HttpResponse.BodyHandlers.ofString());
		var two=client.sendAsync(request,HttpResponse.BodyHandlers.ofString());
		assertThat(Arrays.asList(one.get(20,TimeUnit.SECONDS).statusCode(),two.get(20,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
		String old=currentKey();
		doThrow(new StudentDocumentException(HttpStatus.BAD_GATEWAY,"STUDENT_DOCUMENT_STORAGE_FAILED","삭제 실패")).when(storage).delete(old);
		assertThat(confirm(user,preview(user)).statusCode()).isEqualTo(200);
		assertThat(currentKey()).isNotEqualTo(old); assertThat(files).containsKey(old);
		assertThat(jdbc.queryForObject("select count(*) from student_document_cleanup where object_key=?",Integer.class,old)).isEqualTo(1);
		doAnswer(call -> { files.remove(call.getArgument(0)); return null; }).when(storage).delete(old);
		jdbc.update("update student_document_cleanup set available_at=now()-interval '1 second' where object_key=?",old);
		cleaner.deleteDue(old);
		assertThat(files).doesNotContainKey(old).hasSize(1);
	}

	@Test void documentsUploadConfirmAndReadWithoutAcceptingEditedExtraction() throws Exception {
		var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
		var doc=mapper.readTree(response.body());
		assertThat(doc.path("paths").path(PATH+"/preview").path("post").path("requestBody").path("content").has("multipart/form-data")).isTrue();
		assertThat(doc.path("paths").path(PATH+"/{draftId}/confirm").path("post").has("requestBody")).isFalse();
		assertThat(doc.path("paths").path(PATH+"/preview").path("post").path("responses").has("422")).isTrue();
		for (String suffix : new String[]{"/preview", "/{draftId}/confirm"}) {
			var examples=doc.path("paths").path(PATH+suffix).path("post").path("responses").path("422").path("content").path("application/json").path("examples");
			assertThat(examples.path("UNIVERSITY_UNREADABLE").path("value").path("message").asString()).contains("대학교명");
			assertThat(examples.path("GRADE_UNREADABLE").path("value").path("message").asString()).contains("학년 또는 학기 수");
			assertThat(examples.path("UNIVERSITY_AND_GRADE_UNREADABLE").path("value").path("message").asString()).contains("대학교명과 학년 또는 학기 수");
		}
		assertThat(doc.path("components").path("schemas").path("EnrollmentPreviewResponse").path("properties").has("draftId")).isTrue();
	}

	private Map<String,Object> snapshot() {
		return Map.of("document",jdbc.queryForMap("select * from student_enrollment_document where user_id=?",user),
				"profile",jdbc.queryForMap("select * from student_profile where id=?",user));
	}
	private String currentKey() { return jdbc.queryForObject("select object_key from student_enrollment_document where user_id=?",String.class,user); }
	private String draftKey() { return jdbc.queryForObject("select draft_object_key from student_enrollment_document where user_id=?",String.class,user); }
	private UUID preview(long id) throws Exception {
		var response=upload(id,"%PDF-test","application/pdf"); assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return UUID.fromString(mapper.readTree(response.body()).path("draftId").asString());
	}
	private HttpResponse<String> upload(long id,String bytes,String type) throws Exception {
		String body="--enrollment-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: "+type+"\r\n\r\n"+bytes+"\r\n--enrollment-boundary--\r\n";
		return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+PATH+"/preview"))
				.header("Authorization","Bearer "+token(id)).header("Content-Type","multipart/form-data; boundary=enrollment-boundary")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
	}
	private HttpResponse<String> confirm(long id,UUID draft) throws Exception { return send("POST",PATH+"/"+draft+"/confirm",id); }
	private HttpResponse<String> send(String method,String path,long id) throws Exception { return client.send(request(method,path,id),HttpResponse.BodyHandlers.ofString()); }
	private HttpRequest request(String method,String path,long id) {
		var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).method(method,HttpRequest.BodyPublishers.noBody());
		if(id!=0) { builder.header("Authorization","Bearer "+token(id)); }
		return builder.build();
	}
	private long student(String role) {
		long id=jdbc.queryForObject("insert into user_account(email,role,name) values (?,?,'학생') returning id",Long.class,UUID.randomUUID()+"@example.com",role);
		jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level,university_name,grade_level) values (?,'2000-01-01','VN','서울','BEGINNER','기존학교',1)",id);
		return id;
	}
	private String token(long id) {
		Instant now=Instant.now();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), JwtClaimsSet.builder()
				.issuer("hirelink").subject(Long.toString(id)).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600))
				.id(UUID.randomUUID().toString()).claim("role","STUDENT").claim("token_type","access").build())).getTokenValue();
	}
	private void assertError(HttpResponse<String> response,int status,String code) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		var body=mapper.readTree(response.body()); assertThat(body.path("code").asString()).isEqualTo(code); assertThat(body.size()).isEqualTo(2);
	}
}
