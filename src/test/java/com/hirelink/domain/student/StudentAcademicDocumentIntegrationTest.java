package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
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
import com.hirelink.domain.student.client.AcademicOcrClient;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.dto.AcademicExtraction;
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
class StudentAcademicDocumentIntegrationTest extends PostgreSQLIntegrationTest {
	private static final String PATH = "/api/students/me/documents/transcript";
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired JwtEncoder encoder;
	@Autowired StudentDocumentCleanupService cleaner;
	@MockitoBean AcademicOcrClient ocr;
	@MockitoBean StudentDocumentStorageClient storage;
	@LocalServerPort int port;
	private final HttpClient client = HttpClient.newHttpClient();
	private final Map<String, byte[]> files = new ConcurrentHashMap<>();
	private long user;

	@BeforeEach void setup() {
		jdbc.execute("TRUNCATE TABLE user_account, student_document_cleanup RESTART IDENTITY CASCADE");
		reset(ocr, storage); files.clear();
		when(ocr.recognize(eq("TRANSCRIPT"), any(), anyString())).thenReturn(new AcademicExtraction(2025,"SECOND",new java.math.BigDecimal("4.24"),null));
		when(ocr.recognize(eq("TOPIK"), any(), anyString())).thenReturn(new AcademicExtraction(null,null,null,(short)4));
		doAnswer(call -> { files.put(call.getArgument(0), ((byte[]) call.getArgument(1)).clone()); return null; }).when(storage).upload(anyString(), any(), anyString());
		doAnswer(call -> { files.remove(call.getArgument(0)); return null; }).when(storage).delete(anyString());
		user = student("STUDENT");
	}


    @Test void confirmationPublishesOnlyItsKindAndReplacesOriginalAfterCommit() throws Exception {
        UUID draft=preview(user); String old=draftKey();
        assertThat(jdbc.queryForObject("select previous_semester_gpa from student_profile where id=?",java.math.BigDecimal.class,user)).isNull();
        assertError(send("GET",PATH,user),404,"DOCUMENT_NOT_REGISTERED");
        assertThat(confirm(user,draft).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select previous_semester_gpa from student_profile where id=?",java.math.BigDecimal.class,user)).isEqualByComparingTo("4.24");
        var topik=uploadKind("topik");
        assertThat(jdbc.queryForObject("select topik_level from student_profile where id=?",Short.class,user)).isNull();
        assertThat(send("POST","/api/students/me/documents/topik/"+topik+"/confirm",user).statusCode()).isEqualTo(200);
        var topikBefore=jdbc.queryForMap("select * from student_enrollment_document where user_id=? and document_type='TOPIK'",user);
        assertThat(jdbc.queryForObject("select topik_level from student_profile where id=?",Short.class,user)).isEqualTo((short)4);
        when(ocr.recognize(eq("TRANSCRIPT"),any(),anyString())).thenReturn(new AcademicExtraction(2026,"FIRST",new java.math.BigDecimal("3.81"),null));
        UUID second=preview(user);
        assertThat(files).containsKey(old).hasSize(3);
        assertThat(mapper.readTree(send("GET",PATH,user).body()).path("extracted").path("averageGpa").decimalValue()).isEqualByComparingTo("4.24");
        assertThat(confirm(user,second).statusCode()).isEqualTo(200);
        assertThat(files).doesNotContainKey(old).hasSize(2);
        assertThat(jdbc.queryForMap("select * from student_enrollment_document where user_id=? and document_type='TOPIK'",user)).isEqualTo(topikBefore);
        assertThat(jdbc.queryForObject("select grade_level from student_profile where id=?",Short.class,user)).isEqualTo((short)1);
        assertError(confirm(user,second),409,"DOCUMENT_DRAFT_NOT_FOUND");
    }

    @Test void badOcrStorageFailureAndCancelledPreviewKeepLastConfirmedValues() throws Exception {
        assertThat(confirm(user,preview(user)).statusCode()).isEqualTo(200);
        var before=snapshot(); String original=currentKey();
        when(ocr.recognize(eq("TRANSCRIPT"),any(),anyString())).thenReturn(new AcademicExtraction(2025,"SECOND",new java.math.BigDecimal("-1"),null));
        assertError(upload(user,"%PDF-bad","application/pdf"),422,"UNREADABLE_STUDENT_DOCUMENT");
        assertThat(snapshot()).isEqualTo(before); assertThat(files).containsOnlyKeys(original);
        when(ocr.recognize(eq("TRANSCRIPT"),any(),anyString())).thenReturn(new AcademicExtraction(2025,"SECOND",new java.math.BigDecimal("3.0"),null));
        doThrow(new StudentDocumentException(HttpStatus.BAD_GATEWAY,"STUDENT_DOCUMENT_STORAGE_FAILED","실패")).when(storage).upload(anyString(),any(),anyString());
        assertError(upload(user,"%PDF-new","application/pdf"),502,"STUDENT_DOCUMENT_STORAGE_FAILED");
        assertThat(snapshot()).isEqualTo(before); assertThat(files).containsOnlyKeys(original);
    }

    @Test void databaseFailureAtConfirmPreservesOriginalAndProfile() throws Exception {
        assertThat(confirm(user,preview(user)).statusCode()).isEqualTo(200);
        when(ocr.recognize(eq("TRANSCRIPT"),any(),anyString())).thenReturn(new AcademicExtraction(2026,"FIRST",new java.math.BigDecimal("3.81"),null));
        UUID draft=preview(user); var before=snapshot(); String original=currentKey();
        jdbc.execute("CREATE FUNCTION fail_academic_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER fail_academic_update BEFORE UPDATE ON student_profile FOR EACH ROW EXECUTE FUNCTION fail_academic_update()");
        try {
            assertError(confirm(user,draft),500,"INTERNAL_SERVER_ERROR");
            assertThat(snapshot()).isEqualTo(before); assertThat(files).containsKey(original);
        } finally {
            jdbc.execute("DROP TRIGGER fail_academic_update ON student_profile"); jdbc.execute("DROP FUNCTION fail_academic_update()");
        }
        assertThat(confirm(user,draft).statusCode()).isEqualTo(200);
    }

    @Test void ownLatestDraftRequiredAndConcurrentConfirmSucceedsOnce() throws Exception {
        UUID old=preview(user); UUID draft=preview(user);
        assertError(confirm(user,old),409,"DOCUMENT_DRAFT_NOT_FOUND");
        assertError(confirm(student("STUDENT"),draft),409,"DOCUMENT_DRAFT_NOT_FOUND");
        assertError(send("POST","/api/students/me/documents/topik/"+draft+"/confirm",user),409,"DOCUMENT_DRAFT_NOT_FOUND");
        var req=request("POST",PATH+"/"+draft+"/confirm",user);
        var one=client.sendAsync(req,HttpResponse.BodyHandlers.ofString()); var two=client.sendAsync(req,HttpResponse.BodyHandlers.ofString());
        assertThat(Arrays.asList(one.get(20,TimeUnit.SECONDS).statusCode(),two.get(20,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
        assertError(send("GET",PATH,0),401,"UNAUTHORIZED");
        assertError(send("GET",PATH,student("EMPLOYER")),403,"FORBIDDEN");
        jdbc.update("update user_account set withdrawn_at=now() where id=?",user);
        assertError(send("GET",PATH,user),403,"WITHDRAWN_ACCOUNT");
    }

    @Test void invalidTopikNeverReplacesVerifiedGradeAndSwaggerHasNoEditedValuesBody() throws Exception {
        UUID draft=uploadKind("topik");
        assertThat(send("POST","/api/students/me/documents/topik/"+draft+"/confirm",user).statusCode()).isEqualTo(200);
        when(ocr.recognize(eq("TOPIK"),any(),anyString())).thenReturn(new AcademicExtraction(null,null,null,(short)7));
        var original=jdbc.queryForMap("select * from student_enrollment_document where user_id=? and document_type='TOPIK'",user);
        var response=uploadResponse("topik"); assertError(response,422,"UNREADABLE_STUDENT_DOCUMENT");
        assertThat(jdbc.queryForMap("select * from student_enrollment_document where user_id=? and document_type='TOPIK'",user)).isEqualTo(original);
        var docs=mapper.readTree(send("GET","/v3/api-docs",0).body());
        var path=docs.path("paths").path("/api/students/me/documents/{kind}/{draftId}/confirm").path("post");
        assertThat(path.isMissingNode()).isFalse(); assertThat(path.has("requestBody")).isFalse();
    }

    private UUID uploadKind(String kind) throws Exception {
        var response=uploadResponse(kind); assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return UUID.fromString(mapper.readTree(response.body()).path("draftId").asString());
    }
    private HttpResponse<String> uploadResponse(String kind) throws Exception {
        String body="--academic-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-test\r\n--academic-boundary--\r\n";
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/students/me/documents/"+kind+"/preview"))
            .header("Authorization","Bearer "+token(user)).header("Content-Type","multipart/form-data; boundary=academic-boundary")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
	private Map<String,Object> snapshot() {
		return Map.of("document",jdbc.queryForMap("select * from student_enrollment_document where user_id=? and document_type='TRANSCRIPT'",user),
				"profile",jdbc.queryForMap("select * from student_profile where id=?",user));
	}
	private String currentKey() { return jdbc.queryForObject("select object_key from student_enrollment_document where user_id=? and document_type='TRANSCRIPT'",String.class,user); }
	private String draftKey() { return jdbc.queryForObject("select draft_object_key from student_enrollment_document where user_id=? and document_type='TRANSCRIPT'",String.class,user); }
	private UUID preview(long id) throws Exception {
		var response=upload(id,"%PDF-test","application/pdf"); assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return UUID.fromString(mapper.readTree(response.body()).path("draftId").asString());
	}
	private HttpResponse<String> upload(long id,String bytes,String type) throws Exception {
		String body="--academic-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: "+type+"\r\n\r\n"+bytes+"\r\n--academic-boundary--\r\n";
		return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+PATH+"/preview"))
				.header("Authorization","Bearer "+token(id)).header("Content-Type","multipart/form-data; boundary=academic-boundary")
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
