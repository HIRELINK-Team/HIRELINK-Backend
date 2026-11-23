package com.hirelink.domain.student;

import com.hirelink.domain.auth.support.LoginSessionFixtures;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.hirelink.domain.auth.client.ApickRegistrationClient;
import com.hirelink.domain.auth.client.RegistrationOcrClient;
import com.hirelink.domain.auth.service.StudentRegistrationRenewalService;
import com.hirelink.domain.student.client.AcademicOcrClient;
import com.hirelink.domain.student.client.EnrollmentOcrClient;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.dto.AcademicExtraction;
import com.hirelink.domain.student.dto.EnrollmentExtraction;
import com.hirelink.domain.student.service.StudentEnrollmentService;
import com.hirelink.domain.student.service.StudentAcademicDocumentService;
import com.hirelink.support.PostgreSQLIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "jwt.secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkwMTI=",
    "registration-verification.enabled=true",
    "registration-verification.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "registration-verification.renewal-cleanup-initial-delay=3600000",
    "student-document.cleanup-initial-delay=3600000",
    "student-working-conditions.refresh-enabled=false"})
class StudentDocumentCompletionIntegrationTest extends PostgreSQLIntegrationTest {
    private static final String PATH = "/api/students/me/documents/completion";
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder encoder;
    @Autowired StudentEnrollmentService enrollments;
    @Autowired StudentAcademicDocumentService academics;
    @Autowired StudentRegistrationRenewalService renewals;
    @MockitoBean AcademicOcrClient academicOcr;
    @MockitoBean EnrollmentOcrClient enrollmentOcr;
    @MockitoBean RegistrationOcrClient registrationOcr;
    @MockitoBean ApickRegistrationClient apick;
    @MockitoBean StudentDocumentStorageClient storage;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private LocalDate today;
    private long user;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE user_account, student_document_cleanup RESTART IDENTITY CASCADE");
        jdbc.update("insert into study_program(program_name) values ('학사')");
        reset(academicOcr, enrollmentOcr, registrationOcr, apick, storage);
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        user = student("STUDENT");
        when(enrollmentOcr.recognize(any(),anyString())).thenReturn(new EnrollmentExtraction("학교","캠퍼스",2,null));
        when(academicOcr.recognize(eq("TRANSCRIPT"),any(),anyString())).thenReturn(new AcademicExtraction(2026,"FIRST",new java.math.BigDecimal("4.24"),null));
        when(academicOcr.recognize(eq("TOPIK"),any(),anyString())).thenReturn(new AcademicExtraction(null,null,null,(short)4));
        when(registrationOcr.recognize(any(),anyString(),eq(true))).thenReturn(mapper.createObjectNode()
            .put("name","LIN MAI").put("nationality","VN").put("visaCode","D-2")
            .put("registrationNumber","0001015000000").put("issueDate","2020-01-01"));
        when(registrationOcr.recognize(any(),anyString(),eq(false))).thenReturn(mapper.createObjectNode()
            .put("stayExpiresOn",today.plusDays(90).toString()).put("serialNumber","00000000001"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
    void eachConfirmedDocumentContributesExactlyTwentyFive(int mask) throws Exception {
        if ((mask & 1) != 0) { verification(user,today); }
        if ((mask & 2) != 0) { complete("ENROLLMENT"); }
        if ((mask & 4) != 0) { complete("TRANSCRIPT"); }
        if ((mask & 8) != 0) { complete("TOPIK"); }
        JsonNode result = current();
        assertThat(result.path("completionPercentage").asInt()).isEqualTo(Integer.bitCount(mask)*25);
        assertThat(result.path("registrationIncluded").asBoolean()).isEqualTo((mask & 1) != 0);
        assertThat(result.path("enrollmentIncluded").asBoolean()).isEqualTo((mask & 2) != 0);
        assertThat(result.path("transcriptIncluded").asBoolean()).isEqualTo((mask & 4) != 0);
        assertThat(result.path("topikIncluded").asBoolean()).isEqualTo((mask & 8) != 0);
        assertThat(result.size()).isEqualTo(5);
    }

    @Test void ocrPreviewDoesNotCountUntilEachFinalConfirmation() throws Exception {
        var enrollment = enrollments.upload(user,pdf());
        var transcript = academics.preview(user,"transcript",pdf());
        var topik = academics.preview(user,"topik",pdf());
        percentage(0);
        enrollments.confirm(user,enrollment.draftId()); percentage(25);
        academics.confirm(user,"transcript",transcript.draftId()); percentage(50);
        academics.confirm(user,"topik",topik.draftId()); percentage(75);
    }

    @Test void expiryDayCountsButFollowingDayDropsOnlyRegistrationAndReadDoesNotRevokeBadge() throws Exception {
        complete("ENROLLMENT"); complete("TRANSCRIPT"); complete("TOPIK"); verification(user,today.plusDays(1));
        jdbc.update("update student_profile set hirelink_badge_issued_at=now() where id=?",user);
        var before = jdbc.queryForMap("select * from student_profile where id=?",user);
        percentage(100);
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today,user);
        percentage(100);
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today.minusDays(1),user);
        JsonNode expired=current();
        assertThat(expired.path("completionPercentage").asInt()).isEqualTo(75);
        assertThat(expired.path("registrationIncluded").asBoolean()).isFalse();
        assertThat(expired.path("enrollmentIncluded").asBoolean()).isTrue();
        assertThat(expired.path("transcriptIncluded").asBoolean()).isTrue();
        assertThat(expired.path("topikIncluded").asBoolean()).isTrue();
        assertThat(jdbc.queryForMap("select * from student_profile where id=?",user)).isEqualTo(before);
    }

    @Test void renewalOnlyRestoresContributionAfterFinalSuccessAndRetainsPreviousConfirmedDocuments() throws Exception {
        complete("ENROLLMENT"); complete("TRANSCRIPT"); complete("TOPIK"); verification(user,today.minusDays(1));
        var originals=jdbc.queryForList("select * from student_enrollment_document where user_id=? order by document_type",user);
        percentage(75);
        UUID id=renewals.front(user,image()).renewalId(); percentage(75);
        renewals.back(user,id,image()); percentage(75);
        renewals.confirm(user,id); percentage(100);
        assertThat(jdbc.queryForList("select * from student_enrollment_document where user_id=? order by document_type",user)).isEqualTo(originals);
    }

    @Test void failedOrAbandonedRenewalCannotRestoreExpiredContributionOrExtendFinalExpiry() throws Exception {
        verification(user,today.minusDays(1));
        UUID id=renewals.front(user,image()).renewalId();
        doThrow(new com.hirelink.global.exception.SocialAuthException(org.springframework.http.HttpStatus.BAD_GATEWAY,"REGISTRATION_VERIFICATION_FAILED","실패"))
            .when(apick).verify(anyString(),any(),any());
        assertThatThrownBy(()->renewals.back(user,id,image())).isInstanceOf(com.hirelink.global.exception.SocialAuthException.class);
        percentage(0);
        reset(apick);
        UUID abandoned=renewals.front(user,image()).renewalId(); renewals.back(user,abandoned,image()); percentage(0);
        assertThat(jdbc.queryForObject("select stay_expires_on from student_registration_verification where user_id=?",LocalDate.class,user)).isEqualTo(today.minusDays(1));
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today,user);
        percentage(25); // 유효한 기존 최종 만료일은 갱신 중에도 유지된다.
    }

    @Test void reuploadPreviewOrOcrFailureKeepsPreviouslyConfirmedContribution() throws Exception {
        var first=enrollments.upload(user,pdf()); enrollments.confirm(user,first.draftId()); percentage(25);
        var before=jdbc.queryForMap("select object_key,registered_at,grade_level,university_name from student_enrollment_document where user_id=?",user);
        enrollments.upload(user,pdf()); percentage(25);
        when(enrollmentOcr.recognize(any(),anyString())).thenThrow(EnrollmentExtraction.unreadable());
        assertThatThrownBy(()->enrollments.upload(user,pdf())).isInstanceOf(com.hirelink.global.exception.StudentDocumentException.class);
        percentage(25);
        assertThat(jdbc.queryForMap("select object_key,registered_at,grade_level,university_name from student_enrollment_document where user_id=?",user)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENROLLMENT", "TRANSCRIPT", "TOPIK"})
    void missingRequiredFieldsNeitherCompleteRegistrationNorIssueOrChangeBadge(String type) throws Exception {
        verification(user,today);
        for (String other : Set.of("ENROLLMENT","TRANSCRIPT","TOPIK")) { if (!other.equals(type)) complete(other); }
        if (type.equals("ENROLLMENT")) {
            when(enrollmentOcr.recognize(any(),anyString())).thenReturn(new EnrollmentExtraction(null,null,null,null));
        } else {
            when(academicOcr.recognize(eq(type),any(),anyString())).thenReturn(new AcademicExtraction(null,null,null,null));
        }
        Runnable upload = () -> {
            if (type.equals("ENROLLMENT")) enrollments.upload(user,pdf());
            else academics.preview(user,type.toLowerCase(Locale.ROOT),pdf());
        };
        assertThatThrownBy(upload::run).isInstanceOf(com.hirelink.global.exception.StudentDocumentException.class);
        percentage(75);
        assertThat(mapper.readTree(get("/api/students/me/badge",user).body()).path("active").asBoolean()).isFalse();
        assertThat(jdbc.queryForMap("select hirelink_badge_issued_at from student_profile where id=?",user).get("hirelink_badge_issued_at")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from student_enrollment_document where user_id=? and document_type=?",Integer.class,user,type)).isZero();

        if (type.equals("ENROLLMENT")) {
            when(enrollmentOcr.recognize(any(),anyString())).thenReturn(new EnrollmentExtraction("학교",null,2,null));
            var draft=enrollments.upload(user,pdf()); enrollments.confirm(user,draft.draftId());
            when(enrollmentOcr.recognize(any(),anyString())).thenReturn(new EnrollmentExtraction(null,null,null,null));
        } else {
            var valid=type.equals("TRANSCRIPT") ? new AcademicExtraction(2026,"FIRST",new java.math.BigDecimal("4.24"),null)
                    : new AcademicExtraction(null,null,null,(short)4);
            when(academicOcr.recognize(eq(type),any(),anyString())).thenReturn(valid);
            var draft=academics.preview(user,type.toLowerCase(Locale.ROOT),pdf());
            academics.confirm(user,type.toLowerCase(Locale.ROOT),draft.draftId());
            when(academicOcr.recognize(eq(type),any(),anyString())).thenReturn(new AcademicExtraction(null,null,null,null));
        }
        percentage(100);
        var badge=get("/api/students/me/badge",user);
        assertThat(badge.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(badge.body()).path("active").asBoolean()).isTrue();
        var profile=jdbc.queryForMap("select * from student_profile where id=?",user);
        assertThat(profile.get("hirelink_badge_issued_at")).isNotNull();
        var documents=jdbc.queryForList("select * from student_enrollment_document where user_id=? order by document_type",user);
        assertThatThrownBy(upload::run).isInstanceOf(com.hirelink.global.exception.StudentDocumentException.class);
        percentage(100);
        assertThat(get("/api/students/me/badge",user).body()).isEqualTo(badge.body());
        assertThat(jdbc.queryForMap("select * from student_profile where id=?",user)).isEqualTo(profile);
        assertThat(jdbc.queryForList("select * from student_enrollment_document where user_id=? order by document_type",user)).isEqualTo(documents);
    }

    @Test void manualProfileExpiryAndAnotherStudentsDocumentsDoNotCount() throws Exception {
        jdbc.update("update student_profile set stay_expiration_date=? where id=?",today.plusYears(1),user);
        long other=student("STUDENT"); verification(other,today.plusDays(1));
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,university_name,grade_level,registered_at) values (?,'ENROLLMENT','other/document','학교',2,now())",other);
        percentage(0);
    }

    @Test void requiresAuthenticationActiveStudentAndProfile() throws Exception {
        error(get(PATH,0),401,"UNAUTHORIZED");
        error(get(PATH,student("EMPLOYER")),403,"FORBIDDEN");
        jdbc.update("delete from student_profile where id=?",user);
        error(get(PATH,user),404,"STUDENT_PROFILE_NOT_FOUND");
        jdbc.update("update user_account set withdrawn_at=now() where id=?",user);
        error(get(PATH,user),403,"WITHDRAWN_ACCOUNT");
    }

    @Test void openApiDocumentsResponseAndReadOnlyEndpoint() throws Exception {
        var schema=mapper.readTree(get("/v3/api-docs",0).body());
        var operation=schema.path("paths").path(PATH).path("get");
        assertThat(operation.isMissingNode()).isFalse(); assertThat(operation.has("requestBody")).isFalse();
        assertThat(operation.path("responses").has("200")).isTrue();
        var fields=schema.path("components").path("schemas").path("StudentDocumentCompletionResponse").path("properties");
        assertThat(fields.has("completionPercentage")).isTrue(); assertThat(fields.has("registrationIncluded")).isTrue();
    }

    private JsonNode current() throws Exception {
        var response=get(PATH,user); assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        return mapper.readTree(response.body());
    }
    private void percentage(int value) throws Exception { assertThat(current().path("completionPercentage").asInt()).isEqualTo(value); }
    private MockMultipartFile pdf() { return new MockMultipartFile("file","document.pdf","application/pdf","%PDF-test".getBytes(StandardCharsets.US_ASCII)); }
    private MockMultipartFile image() { return new MockMultipartFile("file","card.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,0}); }
    private void complete(String kind) {
        switch(kind) {
            case "ENROLLMENT" -> jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,university_name,grade_level,registered_at) values (?,'ENROLLMENT','enrollment/original','학교',2,now())",user);
            case "TRANSCRIPT" -> jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,average_gpa,academic_year,semester,registered_at) values (?,'TRANSCRIPT','transcript/original',4.24,2026,'FIRST',now())",user);
            case "TOPIK" -> jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,topik_level,registered_at) values (?,'TOPIK','topik/original',4,now())",user);
            default -> throw new IllegalArgumentException(kind);
        }
    }
    private long student(String role) {
        long id=jdbc.queryForObject("insert into user_account(email,role,name) values (?,?,'학생') returning id",Long.class,UUID.randomUUID()+"@example.com",role);
        jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level,onboarding_completed_at,visa_code,study_program_id) values (?,'2000-01-01','VN','서울','BEGINNER',now(),'D-2-2',(select id from study_program where program_name='학사'))",id);
        return id;
    }
    private void verification(long id,LocalDate expiry) { jdbc.update("insert into student_registration_verification(user_id,nationality,visa_code,stay_expires_on,card_verified_at,registered_at) values (?,'VN','D-2',?,now(),now())",id,expiry); }
    private HttpResponse<String> get(String path,long id) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET();
        if(id!=0) { request.header("Authorization","Bearer "+token(id)); }
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String token(long id) {
        Instant now=Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().claim("sid", LoginSessionFixtures.create(jdbc, id))
            .issuer("hirelink").subject(Long.toString(id)).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(3600))
            .id(UUID.randomUUID().toString()).claim("role","STUDENT").claim("token_type","access").build())).getTokenValue();
    }
    private void error(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var body=mapper.readTree(response.body()); assertThat(body.path("code").asString()).isEqualTo(code); assertThat(body.size()).isEqualTo(2);
    }
}
