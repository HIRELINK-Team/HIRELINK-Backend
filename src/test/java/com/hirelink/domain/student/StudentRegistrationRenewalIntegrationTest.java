package com.hirelink.domain.student;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.hirelink.domain.auth.client.ApickRegistrationClient;
import com.hirelink.domain.auth.client.RegistrationOcrClient;
import com.hirelink.domain.auth.service.RegistrationRenewalCleanupService;
import com.hirelink.domain.student.client.AcademicOcrClient;
import com.hirelink.domain.student.client.EnrollmentOcrClient;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.dto.AcademicExtraction;
import com.hirelink.domain.student.dto.EnrollmentExtraction;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.support.PostgreSQLIntegrationTest;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "jwt.secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkwMTI=",
    "registration-verification.enabled=true",
    "registration-verification.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "registration-verification.renewal-cleanup-initial-delay=3600000",
    "student-document.cleanup-initial-delay=3600000"})
class StudentRegistrationRenewalIntegrationTest extends PostgreSQLIntegrationTest {
    private static final String PATH="/api/students/me/registration-renewals";
    private static final String NUMBER="0001015000000", SERIAL="00000000001";
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder encoder;
    @Autowired RegistrationRenewalCleanupService cleaner;
    @MockitoBean RegistrationOcrClient ocr;
    @MockitoBean ApickRegistrationClient apick;
    @MockitoBean AcademicOcrClient academic;
    @MockitoBean EnrollmentOcrClient enrollment;
    @MockitoBean StudentDocumentStorageClient storage;
    @LocalServerPort int port;
    private final HttpClient client=HttpClient.newHttpClient();
    private LocalDate today, originalExpiry;
    private long user;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE user_account, student_document_cleanup RESTART IDENTITY CASCADE");
        jdbc.execute("INSERT INTO study_program(program_name) VALUES ('전문학사'),('학사'),('석사'),('박사'),('교환학생') ON CONFLICT (program_name) DO NOTHING");
        reset(ocr,apick,academic,enrollment,storage);
        today=LocalDate.now(ZoneId.of("Asia/Seoul")); originalExpiry=today.minusDays(1);
        user=student("STUDENT"); verification(user,originalExpiry);
        when(ocr.recognize(any(),anyString(),eq(true))).thenReturn(mapper.createObjectNode()
            .put("name","LIN MAI").put("nationality","VN").put("visaCode","D-2")
            .put("registrationNumber",NUMBER).put("issueDate","2020-01-01"));
        backExpiry(today.plusDays(90)); pdf(NUMBER,today.plusDays(180));
        when(enrollment.recognize(any(),anyString())).thenReturn(new EnrollmentExtraction("학교","캠퍼스",2,null));
        when(academic.recognize(eq("TRANSCRIPT"),any(),anyString())).thenReturn(new AcademicExtraction(2026,"FIRST",new java.math.BigDecimal("4.24"),null));
        when(academic.recognize(eq("TOPIK"),any(),anyString())).thenReturn(new AcademicExtraction(null,null,null,(short)4));
    }

    @Test void finalRenewalAlonePublishesExpiryAndReissuesWithoutTouchingAcademicDocuments() throws Exception {
        documents(); oldBadge(); var before=snapshot(); var originals=jdbc.queryForList("select * from student_enrollment_document where user_id=?",user);
        UUID id=front(); assertThat(snapshot()).isEqualTo(before);
        String encrypted=jdbc.queryForObject("select renewal_payload from student_registration_verification where user_id=?",String.class,user);
        assertThat(encrypted).isNotBlank().doesNotContain(NUMBER,"2020-01-01");
        var back=back(id); assertThat(back.statusCode()).as(back.body()).isEqualTo(200);
        var result=mapper.readTree(back.body()); assertThat(result.path("requiresStayDocument").asBoolean()).isFalse();
        assertThat(result.has("permissionDate")).isFalse(); assertThat(result.has("reportDate")).isFalse();
        assertThat(snapshot()).isEqualTo(before); assertThat(badge().path("active").asBoolean()).isFalse();
        var confirmed=confirm(id); assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
        assertThat(mapper.readTree(confirmed.body()).path("active").asBoolean()).isTrue();
        assertThat(mapper.readTree(confirmed.body()).path("stayExpirationDate").asString()).isEqualTo(today.plusDays(90).toString());
        assertThat(issued()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(jdbc.queryForObject("select stay_expiration_date from student_profile where id=?",LocalDate.class,user)).isEqualTo(today.plusDays(90));
        assertThat(jdbc.queryForObject("select visa_code from student_profile where id=?",String.class,user)).isEqualTo("D-2-2");
        assertThat(jdbc.queryForObject("select renewal_payload from student_registration_verification where user_id=?",String.class,user)).isNull();
        assertThat(jdbc.queryForList("select * from student_enrollment_document where user_id=?",user)).isEqualTo(originals);
        error(confirm(id),409,"REGISTRATION_RENEWAL_NOT_FOUND");
        verify(apick,times(1)).verify(NUMBER,LocalDate.of(2020,1,1),SERIAL); verifyNoInteractions(storage);
    }

    @Test void expiredCardUsesMatchingPdfWithoutSecondAuthenticityCallAndWaitsForConfirm() throws Exception {
        documents(); oldBadge(); var before=snapshot(); backExpiry(today.minusDays(2));
        UUID id=front(); assertThat(back(id).statusCode()).isEqualTo(200);
        error(confirm(id),409,"VALID_STAY_DOCUMENT_REQUIRED");
        pdf("0001016000000",today.plusDays(30)); error(document(id),422,"STAY_DOCUMENT_MISMATCH");
        pdf(NUMBER,today.minusDays(1)); error(document(id),422,"STAY_DOCUMENT_EXPIRED");
        assertThat(snapshot()).isEqualTo(before);
        pdf(NUMBER,today); var response=document(id); assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("serialNumber").asString()).isEqualTo(SERIAL);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(confirm(id).statusCode()).isEqualTo(200); assertThat(badge().path("active").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select document_verified_at from student_registration_verification where user_id=?",java.sql.Timestamp.class,user)).isNotNull();
        verify(apick,times(1)).verify(NUMBER,LocalDate.of(2020,1,1),SERIAL);
    }

    @ParameterizedTest @ValueSource(strings={"enrollment","transcript","topik"})
    void lastFinalDocumentIssuesBadgeButPreviewAndReuploadDoNot(String last) throws Exception {
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today,user);
        for (String kind:List.of("enrollment","transcript","topik")) {
            if (!kind.equals(last)) { confirmDocument(kind,previewDocument(kind)); }
        }
        assertThat(issued()).isNull();
        UUID draft=previewDocument(last); assertThat(issued()).isNull(); assertThat(badge().path("active").asBoolean()).isFalse();
        confirmDocument(last,draft); assertThat(badge().path("active").asBoolean()).isTrue(); Instant issued=issued();
        confirmDocument(last,previewDocument(last)); assertThat(issued()).isEqualTo(issued);
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today.minusDays(1),user);
        assertThat(badge().path("active").asBoolean()).isFalse(); assertThat(issued()).isEqualTo(issued);
    }

    @Test void incompleteDocumentsAndExpiredOrUnverifiedCardNeverIssue() throws Exception {
        UUID id=front(); back(id); assertThat(confirm(id).statusCode()).isEqualTo(200);
        assertThat(issued()).isNull();
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today.minusDays(1),user);
        for (String kind:List.of("enrollment","transcript","topik")) { confirmDocument(kind,previewDocument(kind)); }
        assertThat(issued()).isNull();
        jdbc.update("delete from student_registration_verification where user_id=?",user);
        confirmDocument("topik",previewDocument("topik")); assertThat(issued()).isNull();
        error(upload(PATH+"/front",user,false),409,"REGISTRATION_REQUIRED");
    }

    @Test void recaptureFailureInvalidatesDraftButPreservesConfirmedExpiryAndBadge() throws Exception {
        documents(); oldBadge(); var before=snapshot(); UUID old=front(); back(old);
        when(ocr.recognize(any(),anyString(),eq(true))).thenThrow(RegistrationOcrClient.unreadable());
        error(upload(PATH+"/front",user,false),422,"UNREADABLE_REGISTRATION");
        error(confirm(old),409,"REGISTRATION_RENEWAL_NOT_FOUND"); assertThat(snapshot()).isEqualTo(before);
    }

    @Test void authenticityFailureAndMalformedVisaCannotConfirm() throws Exception {
        var before=snapshot(); UUID id=front();
        doThrow(new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REGISTRATION","진위 확인 실패")).when(apick).verify(anyString(),any(),any());
        error(back(id),422,"INVALID_REGISTRATION"); error(confirm(id),409,"REGISTRATION_BACK_REQUIRED");
        assertThat(snapshot()).isEqualTo(before);
        when(ocr.recognize(any(),anyString(),eq(true))).thenReturn(mapper.createObjectNode()
            .put("name","LIN MAI").put("nationality","VN").put("visaCode","UNKNOWN").put("registrationNumber",NUMBER).put("issueDate","2020-01-01"));
        error(upload(PATH+"/front",user,false),422,"UNREADABLE_REGISTRATION");
    }

    @Test void onlyLatestOwnDraftIsAcceptedAndExpiredDraftIsCleaned() throws Exception {
        UUID old=front(), id=front(); error(back(old),409,"REGISTRATION_RENEWAL_NOT_FOUND");
        long other=student("STUDENT"); verification(other,today);
        error(upload(PATH+"/"+id+"/back",other,false),409,"REGISTRATION_RENEWAL_NOT_FOUND");
        var before=snapshot(); jdbc.update("update student_registration_verification set renewal_expires_at=now()-interval '1 second' where user_id=?",user);
        cleaner.clearExpired(); assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select renewal_payload from student_registration_verification where user_id=?",String.class,user)).isNull();
        error(back(id),409,"REGISTRATION_RENEWAL_NOT_FOUND");
        id=front(); jdbc.update("update student_registration_verification set renewal_expires_at=now()-interval '1 second' where user_id=?",user);
        error(back(id),409,"REGISTRATION_RENEWAL_EXPIRED"); assertThat(snapshot()).isEqualTo(before);
    }

    @Test void concurrentConfirmationSucceedsOnceAndActiveRenewalKeepsIssueTime() throws Exception {
        documents(); oldBadge(); jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today,user);
        Instant issued=issued(); UUID id=front(); back(id);
        var request=request("POST",PATH+"/"+id+"/confirm",user);
        var one=client.sendAsync(request,HttpResponse.BodyHandlers.ofString()); var two=client.sendAsync(request,HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(one.get(20,TimeUnit.SECONDS).statusCode(),two.get(20,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
        assertThat(issued()).isEqualTo(issued);
    }

    @Test void databaseFailureRollsBackFinalExpiryBadgeAndDraftConsumption() throws Exception {
        documents(); oldBadge(); UUID id=front(); back(id); var before=snapshot();
        jdbc.execute("CREATE FUNCTION fail_renewal() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER fail_renewal BEFORE UPDATE ON student_profile FOR EACH ROW EXECUTE FUNCTION fail_renewal()");
        try { error(confirm(id),500,"INTERNAL_SERVER_ERROR"); assertThat(snapshot()).isEqualTo(before); }
        finally { jdbc.execute("DROP TRIGGER fail_renewal ON student_profile"); jdbc.execute("DROP FUNCTION fail_renewal()"); }
        assertThat(confirm(id).statusCode()).isEqualTo(200);
    }

    @Test void roleAuthenticationWithdrawalAndManualExpiryBypassAreBlocked() throws Exception {
        error(send("GET","/api/students/me/badge",0),401,"UNAUTHORIZED");
        long employer=student("EMPLOYER"); error(upload(PATH+"/front",employer,false),403,"FORBIDDEN");
        error(send("GET","/api/students/me/badge",employer),403,"FORBIDDEN");
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/students/me/profile"))
            .header("Authorization","Bearer "+token(user)).header("Content-Type","application/json")
            .method("PATCH",HttpRequest.BodyPublishers.ofString("{\"stayExpirationDate\":\"2030-01-01\"}")).build();
        assertThat(client.send(req,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
        jdbc.update("update user_account set withdrawn_at=now() where id=?",user);
        error(upload(PATH+"/front",user,false),403,"WITHDRAWN_ACCOUNT");
    }

    @Test void swaggerDefinesFinalConfirmationWithoutEditableOcrFields() throws Exception {
        var docs=mapper.readTree(send("GET","/v3/api-docs",0).body());
        var operation=docs.path("paths").path(PATH+"/{renewalId}/confirm").path("post");
        assertThat(operation.isMissingNode()).isFalse(); assertThat(operation.has("requestBody")).isFalse();
        assertThat(docs.path("paths").has("/api/students/me/badge")).isTrue();
        var confirmation=docs.path("components").path("schemas").path("RegistrationRenewalConfirmationResponse");
        assertThat(confirmation.path("properties").toString()).contains("serviceSupported","registrationVisaCode","studyProgram");
        assertThat(docs.path("paths").path("/api/students/me/registration").has("get")).isTrue();
        var notice=docs.path("paths").path("/api/students/me/stay-expiration-notice/claim").path("post");
        assertThat(notice.has("requestBody")).isFalse();
        assertThat(notice.path("responses").path("403").toString()).contains("ONBOARDING_REQUIRED");
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void unsupportedVisaPublishesAllVerifiedInformationOnlyAtFinalConfirmation(boolean usePdf) throws Exception {
        documents(); oldBadge(); var before=snapshot(); var originals=jdbc.queryForList("select * from student_enrollment_document where user_id=?",user);
        when(ocr.recognize(any(),anyString(),eq(true))).thenReturn(mapper.createObjectNode()
            .put("name","UPDATED NAME").put("nationality","KR").put("visaCode","D-10")
            .put("registrationNumber",NUMBER).put("issueDate","2020-01-01"));
        if (usePdf) { backExpiry(today.minusDays(1)); }
        UUID id=front(); assertThat(back(id).statusCode()).isEqualTo(200);
        if (usePdf) { assertThat(document(id).statusCode()).isEqualTo(200); }
        assertThat(snapshot()).isEqualTo(before);
        var pending=mapper.readTree(send("GET","/api/students/me/registration",user).body());
        assertThat(pending.path("registrationVisaCode").asString()).isEqualTo("D-2");
        assertThat(pending.path("name").asString()).isEqualTo("학생");
        var response=confirm(id); assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var result=mapper.readTree(response.body());
        assertThat(result.path("name").asString()).isEqualTo("UPDATED NAME");
        assertThat(result.path("nationalityCode").asString()).isEqualTo("KR");
        assertThat(result.path("registrationVisaCode").asString()).isEqualTo("D-10");
        assertThat(result.path("visaCode").asString()).isEqualTo("D-10");
        assertThat(result.path("studyProgram").asString()).isEqualTo("BACHELOR");
        assertThat(result.path("serviceSupported").asBoolean()).isFalse();
        assertThat(result.path("supportMessage").asString()).contains("서비스 지원 대상");
        assertThat(issued()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(jdbc.queryForMap("select nationality,visa_code from student_profile where id=?",user))
            .containsEntry("nationality","KR").containsEntry("visa_code","D-10");
        assertThat(jdbc.queryForObject("select visa_code from student_registration_verification where user_id=?",String.class,user)).isEqualTo("D-10");
        assertThat(jdbc.queryForList("select * from student_enrollment_document where user_id=?",user)).isEqualTo(originals);
        assertThat(mapper.readTree(send("GET","/api/students/me/profile",user).body()).path("visaCode").asString()).isEqualTo("D-10");
        assertThat(mapper.readTree(send("GET","/api/students/me/registration",user).body())).isEqualTo(result);
        verify(apick,times(1)).verify(NUMBER,LocalDate.of(2020,1,1),SERIAL);
    }

    @Test void returningToD2UsesSavedProgramAndCanReissueWithExistingDocuments() throws Exception {
        documents(); oldBadge();
        jdbc.update("update student_registration_verification set visa_code='D-10' where user_id=?",user);
        jdbc.update("update student_profile set visa_code='D-10',study_program_id=(select id from study_program where program_name='석사') where id=?",user);
        var id=front(); back(id);
        var response=confirm(id); assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var result=mapper.readTree(response.body());
        assertThat(result.path("visaCode").asString()).isEqualTo("D-2-3");
        assertThat(result.path("registrationVisaCode").asString()).isEqualTo("D-2");
        assertThat(result.path("studyProgram").asString()).isEqualTo("MASTER");
        assertThat(result.path("serviceSupported").asBoolean()).isTrue();
        assertThat(result.path("active").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select name from user_account where id=?",String.class,user)).isEqualTo("LIN MAI");
        assertThat(issued()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test void missingSavedProgramDoesNotInventDetailedVisaOrIssueNewBadge() throws Exception {
        documents();
        jdbc.update("update student_profile set study_program_id=null where id=?",user);
        var id=front(); back(id); var response=confirm(id);
        assertThat(response.statusCode()).isEqualTo(200);
        var result=mapper.readTree(response.body());
        assertThat(result.path("visaCode").asString()).isEqualTo("D-2");
        assertThat(result.path("serviceSupported").asBoolean()).isFalse();
        assertThat(issued()).isNull();
    }

    @ParameterizedTest @ValueSource(ints={31,30,29,1,0,-1})
    void expiryNoticeUsesActualRemainingDaysAndOnlyClaimsOnce(int remaining) throws Exception {
        var expiry=today.plusDays(remaining);
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",expiry,user);
        var before=snapshot();
        var first=notice(user); boolean eligible=remaining>=0 && remaining<=30;
        assertThat(first.path("show").asBoolean()).isEqualTo(eligible);
        assertThat(first.path("remainingDays").asLong()).isEqualTo(remaining);
        assertThat(first.path("stayExpirationDate").asString()).isEqualTo(expiry.toString());
        if (eligible) {
            assertThat(first.path("message").asString()).isEqualTo(remaining==0?"오늘 체류기간이 만료돼요":"체류기간 만료까지 "+remaining+"일 남았어요.");
        } else { assertThat(first.path("message").isNull()).isTrue(); }
        assertThat(notice(user).path("show").asBoolean()).isFalse();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select jsonb_array_length(expiry_notice_dates) from student_registration_verification where user_id=?",Integer.class,user)).isEqualTo(eligible?1:0);
        verifyNoInteractions(ocr,apick,academic,enrollment,storage);
    }

    @Test void noticeRemembersEachExpiryAcrossSuccessfulRenewalsAndReturningToAnOldDate() throws Exception {
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today.plusDays(20),user);
        assertThat(notice(user).path("show").asBoolean()).isTrue();
        for (int day:List.of(20,21,20)) {
            backExpiry(today.plusDays(day)); var id=front(); back(id);
            assertThat(notice(user).path("show").asBoolean()).isFalse();
            assertThat(confirm(id).statusCode()).isEqualTo(200);
            assertThat(notice(user).path("show").asBoolean()).isEqualTo(day==21);
        }
        assertThat(jdbc.queryForObject("select jsonb_array_length(expiry_notice_dates) from student_registration_verification where user_id=?",Integer.class,user)).isEqualTo(2);
    }

    @Test void concurrentNoticeRequestsOnlyGrantOneDisplay() throws Exception {
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today.plusDays(29),user);
        var request=request("POST","/api/students/me/stay-expiration-notice/claim",user);
        var one=client.sendAsync(request,HttpResponse.BodyHandlers.ofString());
        var two=client.sendAsync(request,HttpResponse.BodyHandlers.ofString());
        var responses=List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        assertThat(responses).allSatisfy(response->assertThat(response.statusCode()).as(response.body()).isEqualTo(200));
        assertThat(responses.stream().filter(response->mapper.readTree(response.body()).path("show").asBoolean()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select jsonb_array_length(expiry_notice_dates) from student_registration_verification where user_id=?",Integer.class,user)).isEqualTo(1);
    }

    @Test void failedNoticeSaveDoesNotConsumeDisplay() throws Exception {
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today,user);
        jdbc.execute("CREATE FUNCTION fail_notice() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER fail_notice BEFORE UPDATE ON student_registration_verification FOR EACH ROW EXECUTE FUNCTION fail_notice()");
        try {
            error(send("POST","/api/students/me/stay-expiration-notice/claim",user),500,"INTERNAL_SERVER_ERROR");
            assertThat(jdbc.queryForObject("select jsonb_array_length(expiry_notice_dates) from student_registration_verification where user_id=?",Integer.class,user)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_notice ON student_registration_verification");
            jdbc.execute("DROP FUNCTION fail_notice()");
        }
        assertThat(notice(user).path("show").asBoolean()).isTrue();
    }

    @Test void newRegistrationEndpointsRequireOwnActiveCompletedStudent() throws Exception {
        String notice="/api/students/me/stay-expiration-notice/claim", current="/api/students/me/registration";
        error(send("POST",notice,0),401,"UNAUTHORIZED");
        error(send("GET",current,0),401,"UNAUTHORIZED");
        long employer=student("EMPLOYER");
        error(send("POST",notice,employer),403,"FORBIDDEN");
        error(send("GET",current,employer),403,"FORBIDDEN");
        jdbc.update("update student_profile set onboarding_completed_at=null where id=?",user);
        error(send("POST",notice,user),403,"ONBOARDING_REQUIRED");
        error(send("GET",current,user),403,"ONBOARDING_REQUIRED");
        jdbc.update("update student_profile set onboarding_completed_at=now() where id=?",user);
        jdbc.update("update user_account set withdrawn_at=now() where id=?",user);
        error(send("POST",notice,user),403,"WITHDRAWN_ACCOUNT");
        error(send("GET",current,user),403,"WITHDRAWN_ACCOUNT");
        long legacy=student("STUDENT");
        assertThat(notice(legacy).path("show").asBoolean()).isFalse();
        error(send("GET",current,legacy),409,"REGISTRATION_REQUIRED");
    }

    @Test void incompleteLegacyDraftRequiresRecaptureWithoutOverwritingConfirmedValues() throws Exception {
        var before=snapshot(); var id=front(); back(id);
        jdbc.update("update student_registration_verification set renewal_payload=null where user_id=?",user);
        error(confirm(id),409,"REGISTRATION_FRONT_REQUIRED");
        assertThat(snapshot()).isEqualTo(before);
    }

    private tools.jackson.databind.JsonNode notice(long member) throws Exception {
        var response=send("POST","/api/students/me/stay-expiration-notice/claim",member);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        return mapper.readTree(response.body());
    }

    private void backExpiry(LocalDate expiry) {
        when(ocr.recognize(any(),anyString(),eq(false))).thenReturn(mapper.createObjectNode().put("stayExpiresOn",expiry.toString()).put("serialNumber",SERIAL));
    }
    private void pdf(String number,LocalDate expiry) {
        when(ocr.recognizeStayDocument(any())).thenReturn(mapper.createObjectNode().put("registrationNumber",number).put("stayExpiresOn",expiry.toString()));
    }
    private void documents() {
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,university_name,grade_level,registered_at) values (?,'ENROLLMENT','enrollment/original','학교',2,now())",user);
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,average_gpa,academic_year,semester,registered_at) values (?,'TRANSCRIPT','transcript/original',4.24,2026,'FIRST',now())",user);
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,topik_level,registered_at) values (?,'TOPIK','topik/original',4,now())",user);
    }
    private void oldBadge() { jdbc.update("update student_profile set hirelink_badge_issued_at='2020-01-01T00:00:00Z' where id=?",user); }
    private Instant issued() { var value=jdbc.queryForObject("select hirelink_badge_issued_at from student_profile where id=?",java.sql.Timestamp.class,user); return value==null?null:value.toInstant(); }
    private tools.jackson.databind.JsonNode badge() throws Exception { var response=send("GET","/api/students/me/badge",user); assertThat(response.statusCode()).isEqualTo(200); return mapper.readTree(response.body()); }
    private Map<String,Object> snapshot() {
        return Map.of("registration",jdbc.queryForMap("select nationality,visa_code,stay_expires_on,card_verified_at,document_verified_at,registered_at,birth_date from student_registration_verification where user_id=?",user),
            "profile",jdbc.queryForMap("select * from student_profile where id=?",user),
            "account",jdbc.queryForMap("select * from user_account where id=?",user));
    }
    private UUID front() throws Exception { var response=upload(PATH+"/front",user,false); assertThat(response.statusCode()).as(response.body()).isEqualTo(200); return UUID.fromString(mapper.readTree(response.body()).path("renewalId").asString()); }
    private HttpResponse<String> back(UUID id) throws Exception { return upload(PATH+"/"+id+"/back",user,false); }
    private HttpResponse<String> document(UUID id) throws Exception { return upload(PATH+"/"+id+"/stay-document",user,true); }
    private HttpResponse<String> confirm(UUID id) throws Exception { return send("POST",PATH+"/"+id+"/confirm",user); }
    private UUID previewDocument(String kind) throws Exception { var response=upload("/api/students/me/documents/"+kind+"/preview",user,true); assertThat(response.statusCode()).as(response.body()).isEqualTo(200); return UUID.fromString(mapper.readTree(response.body()).path("draftId").asString()); }
    private void confirmDocument(String kind,UUID id) throws Exception { var response=send("POST","/api/students/me/documents/"+kind+"/"+id+"/confirm",user); assertThat(response.statusCode()).as(response.body()).isEqualTo(200); }
    private HttpResponse<String> upload(String path,long id,boolean pdf) throws Exception {
        String type=pdf?"application/pdf":"image/jpeg";
        String header="--renewal-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"file\"\r\nContent-Type: "+type+"\r\n\r\n";
        var bytes=new java.io.ByteArrayOutputStream(); bytes.write(header.getBytes(StandardCharsets.UTF_8));
        bytes.write(pdf?"%PDF-test".getBytes(StandardCharsets.US_ASCII):new byte[]{(byte)255,(byte)216,(byte)255,0});
        bytes.write("\r\n--renewal-boundary--\r\n".getBytes(StandardCharsets.US_ASCII));
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Authorization","Bearer "+token(id))
            .header("Content-Type","multipart/form-data; boundary=renewal-boundary").POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    private long student(String role) {
        long id=jdbc.queryForObject("insert into user_account(email,role,name) values (?,?,'학생') returning id",Long.class,UUID.randomUUID()+"@example.com",role);
        jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level,visa_code,stay_expiration_date,onboarding_completed_at) values (?,'2000-01-01','VN','서울','BEGINNER','D-2-2',?,now())",id,originalExpiry);
        jdbc.update("update student_profile set study_program_id=(select id from study_program where program_name='학사') where id=?",id);
        jdbc.update("update user_account set phone_number=?,phone_verified_at=now() where id=?",String.format(java.util.Locale.ROOT,"010%08d",id),id);
        return id;
    }
    private void verification(long id,LocalDate expiry) {
        jdbc.update("insert into student_registration_verification(user_id,nationality,visa_code,stay_expires_on,card_verified_at,registered_at,birth_date) values (?,'VN','D-2',?,now(),now(),'2000-01-01')",id,expiry);
    }
    private HttpResponse<String> send(String method,String path,long id) throws Exception { return client.send(request(method,path,id),HttpResponse.BodyHandlers.ofString()); }
    private HttpRequest request(String method,String path,long id) {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).method(method,HttpRequest.BodyPublishers.noBody());
        if(id!=0) { builder.header("Authorization","Bearer "+token(id)); } return builder.build();
    }
    private String token(long id) {
        Instant now=Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
            .issuer("hirelink").subject(Long.toString(id)).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600))
            .id(UUID.randomUUID().toString()).claim("role","STUDENT").claim("token_type","access").build())).getTokenValue();
    }
    private void error(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var body=mapper.readTree(response.body()); assertThat(body.path("code").asString()).isEqualTo(code); assertThat(body.size()).isEqualTo(2);
    }
}
