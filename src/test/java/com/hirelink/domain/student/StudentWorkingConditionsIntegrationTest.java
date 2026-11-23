package com.hirelink.domain.student;

import com.hirelink.domain.auth.support.LoginSessionFixtures;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import com.hirelink.domain.student.service.*;
import com.hirelink.global.exception.StudentDocumentException;
import com.hirelink.support.PostgreSQLIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "jwt.secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkwMTI=",
    "registration-verification.enabled=true",
    "registration-verification.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "registration-verification.renewal-cleanup-initial-delay=3600000",
    "student-document.cleanup-initial-delay=3600000",
    "student-working-conditions.refresh-enabled=false"})
class StudentWorkingConditionsIntegrationTest extends PostgreSQLIntegrationTest {
    private static final String PATH="/api/students/me/working-conditions";
    private static final LocalDate INITIAL=LocalDate.of(2026,11,23);
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder encoder;
    @Autowired StudentAcademicDocumentService academics;
    @Autowired StudentEnrollmentService enrollments;
    @Autowired StudentRegistrationRenewalService renewals;
    @Autowired AccreditedUniversityService universities;
    @Autowired StudentWorkingConditionsService conditions;
    @Autowired com.hirelink.domain.student.repository.StudentProfileRepository profiles;
    @MockitoBean Clock clock;
    @MockitoBean AcademicOcrClient academicOcr;
    @MockitoBean EnrollmentOcrClient enrollmentOcr;
    @MockitoBean RegistrationOcrClient registrationOcr;
    @MockitoBean ApickRegistrationClient apick;
    @MockitoBean StudentDocumentStorageClient storage;
    @LocalServerPort int port;
    private final HttpClient client=HttpClient.newHttpClient();
    private long user;
    private LocalDate today;
    private final List<LocalDate> testCatalogs=new ArrayList<>();

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE user_account, student_document_cleanup RESTART IDENTITY CASCADE");
        jdbc.execute("INSERT INTO study_program(program_name) VALUES ('전문학사'),('학사'),('석사'),('박사'),('교환학생') ON CONFLICT (program_name) DO NOTHING");
        reset(academicOcr,enrollmentOcr,registrationOcr,apick,storage);
        today=LocalDate.now(ZoneId.of("Asia/Seoul")); user=student("STUDENT");
        clockDate(today);
        ready(user);
        when(academicOcr.recognize(eq("TRANSCRIPT"),any(),anyString())).thenReturn(new AcademicExtraction(2026,"FIRST",new java.math.BigDecimal("4.24"),null));
        when(academicOcr.recognize(eq("TOPIK"),any(),anyString())).thenReturn(new AcademicExtraction(null,null,null,(short)5));
        when(enrollmentOcr.recognize(any(),anyString())).thenReturn(new EnrollmentExtraction("숭실대학교(본교)","다른캠퍼스",3,null));
        when(registrationOcr.recognize(any(),anyString(),eq(true))).thenReturn(mapper.createObjectNode()
            .put("name","LIN MAI").put("nationality","VN").put("visaCode","D-2").put("registrationNumber","0001015000000").put("issueDate","2020-01-01"));
        when(registrationOcr.recognize(any(),anyString(),eq(false))).thenReturn(mapper.createObjectNode()
            .put("stayExpiresOn",today.plusDays(90).toString()).put("serialNumber","00000000001"));
    }
    @AfterEach void removeTestCatalogs() {
        for(LocalDate date:testCatalogs) {
            jdbc.update("delete from accredited_university_entry where effective_from=?",date);
            jdbc.update("delete from accredited_university_catalog where effective_from=?",date);
        }
        testCatalogs.clear();
    }

    @Test void responseUsesFinalDocumentsNotEditableOrDraftProfileValues() throws Exception {
        jdbc.update("update student_profile set university_name='다른학교', grade_level=1, topik_level=1, previous_semester_gpa=0, academic_state='LEAVE' where id=?",user);
        var result=current();
        assertThat(result.path("basicInformation").path("gradeLevel").asInt()).isEqualTo(3);
        assertThat(result.path("basicInformation").path("topikLevel").asInt()).isEqualTo(4);
        assertThat(result.path("estimate").path("requiredTopikLevel").asInt()).isEqualTo(4);
        assertThat(result.path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(30);
        assertThat(result.path("estimate").path("weekendsAndHolidays").path("type").asString()).isEqualTo("NO_TIME_CAP");
        assertThat(result.path("estimate").path("vacations").path("type").asString()).isEqualTo("NO_TIME_CAP");
        assertThat(result.path("notice").asString()).contains("예상","허가 완료를 의미하지");
    }
    @Test void currentBadgeIsRequiredAndSuccessfulRenewalRestoresAccess() throws Exception {
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today,user);
        assertThat(get(PATH,user).statusCode()).isEqualTo(200);
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today.minusDays(1),user);
        error(get(PATH,user),403,"ACTIVE_BADGE_REQUIRED");
        var id=renewals.front(user,image()).renewalId(); renewals.back(user,id,image());
        error(get(PATH,user),403,"ACTIVE_BADGE_REQUIRED");
        renewals.confirm(user,id);
        assertThat(stored().path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(30);
        assertThat(get(PATH,user).statusCode()).isEqualTo(200);
        jdbc.update("update student_profile set hirelink_badge_issued_at=null where id=?",user);
        error(get(PATH,user),403,"ACTIVE_BADGE_REQUIRED");
    }
    @Test void eachRequiredFinalDocumentMustStillExistEvenWithHistoricalIssueTime() throws Exception {
        for(String kind:List.of("ENROLLMENT","TRANSCRIPT","TOPIK")) {
            jdbc.update("update student_enrollment_document set registered_at=null,object_key=null where user_id=? and document_type=?",user,kind);
            error(get(PATH,user),403,"ACTIVE_BADGE_REQUIRED");
            jdbc.update("update student_enrollment_document set registered_at=now(),object_key=? where user_id=? and document_type=?",kind+"/original",user,kind);
        }
    }
    @Test void finalTranscriptAndTopikUpdatesRecalculateButPreviewsAndFailuresDoNot() throws Exception {
        school("미인증대학교"); weekday(25);
        var transcript=academics.preview(user,"transcript",pdf()); weekday(25);
        academics.confirm(user,"transcript",transcript.draftId()); weekday(30);
        when(academicOcr.recognize(eq("TRANSCRIPT"),any(),anyString())).thenThrow(AcademicExtraction.unreadable());
        assertThatThrownBy(()->academics.preview(user,"transcript",pdf())).isInstanceOf(StudentDocumentException.class); weekday(30);
        jdbc.update("update student_enrollment_document set topik_level=3 where user_id=? and document_type='TOPIK'",user);
        var before=current().path("estimate");
        assertThat(before.path("combinedWeeklyLimitHours").asInt()).isEqualTo(10);
        assertThat(before.path("additionalWeekdayHours").asInt()).isZero();
        var topik=academics.preview(user,"topik",pdf());
        assertThat(current().path("estimate").path("combinedWeeklyLimitHours").asInt()).isEqualTo(10);
        academics.confirm(user,"topik",topik.draftId()); weekday(30);
    }
    @Test void enrollmentFinalConfirmationRecalculatesSchoolAndGradeAndIgnoresCampus() throws Exception {
        school("미인증대학교"); weekday(25);
        var draft=enrollments.upload(user,pdf()); weekday(25);
        enrollments.confirm(user,draft.draftId()); weekday(30);
        assertThat(current().path("estimate").path("accreditedUniversity").asBoolean()).isTrue();
        when(enrollmentOcr.recognize(any(),anyString())).thenReturn(new EnrollmentExtraction("학교",null,5,null));
        assertThatThrownBy(()->enrollments.upload(user,pdf())).isInstanceOf(StudentDocumentException.class); weekday(30);
    }
    @Test void datedCatalogSelectsNewestEffectiveVersionWithoutTouchingDocuments() {
        LocalDate future=today.plusDays(1);
        catalog(future,"새인증대학교");
        assertThat(universities.lookup("숭실대학교",today).accredited()).isTrue();
        assertThat(universities.lookup("새인증대학교",today).accredited()).isFalse();
        assertThat(universities.lookup("숭실대학교",future).accredited()).isFalse();
        var changed=universities.lookup("새인증대학교",future);
        assertThat(changed.accredited()).isTrue(); assertThat(changed.effectiveFrom()).isEqualTo(future);
        assertThatThrownBy(()->universities.lookup("숭실대학교",INITIAL.minusDays(1))).isInstanceOf(StudentDocumentException.class);
        verifyNoInteractions(academicOcr,enrollmentOcr,registrationOcr,apick,storage);
    }
    @Test void changedListRemovesOnlyItsBenefitAndNeverChangesBadgeOrCompletion() throws Exception {
        weekday(30);
        var profile=profileWithoutEstimate();
        var documents=jdbc.queryForList("select * from student_enrollment_document where user_id=? order by document_type",user);
        var completion=get("/api/students/me/documents/completion",user).body();
        // 같은 적용 스냅샷의 테스트 데이터를 교체해 조회 결과가 캐시에 남지 않는지 확인한다.
        jdbc.update("update accredited_university_entry set normalized_name='테스트미인증학교' where effective_from=? and listed_name='숭실대학교'",INITIAL);
        try {
            weekday(25);
            var value=current().path("estimate");
            assertThat(value.path("weekendsAndHolidays").path("type").asString()).isEqualTo("NO_TIME_CAP");
            assertThat(value.path("vacations").path("type").asString()).isEqualTo("NO_TIME_CAP");
            assertThat(profileWithoutEstimate()).isEqualTo(profile);
            assertThat(jdbc.queryForList("select * from student_enrollment_document where user_id=? order by document_type",user)).isEqualTo(documents);
            assertThat(get("/api/students/me/documents/completion",user).body()).isEqualTo(completion);
            jdbc.update("update student_enrollment_document set average_gpa=4.0 where user_id=? and document_type='TRANSCRIPT'",user); weekday(30);
            jdbc.update("update student_enrollment_document set average_gpa=3.99 where user_id=? and document_type='TRANSCRIPT'",user);
            jdbc.update("update student_enrollment_document set topik_level=5 where user_id=? and document_type='TOPIK'",user); weekday(30);
        } finally { jdbc.update("update accredited_university_entry set normalized_name='숭실대학교' where effective_from=? and listed_name='숭실대학교'",INITIAL); }
    }
    @Test void missingCatalogEntriesFailExplicitlyRatherThanSilentlyRemoveBenefits() throws Exception {
        jdbc.update("update accredited_university_catalog set expected_entry_count=185 where effective_from=?",INITIAL);
        try { error(get(PATH,user),503,"ACCREDITED_UNIVERSITY_LIST_UNAVAILABLE"); }
        finally { jdbc.update("update accredited_university_catalog set expected_entry_count=184 where effective_from=?",INITIAL); }
    }
    @Test void missingOrConflictingProgramIsNotInferredFromVisa() throws Exception {
        jdbc.update("update student_profile set study_program_id=null where id=?",user);
        error(get(PATH,user),409,"WORKING_CONDITIONS_DATA_INCOMPLETE");
        jdbc.update("update student_profile set study_program_id=(select id from study_program where program_name='석사') where id=?",user);
        error(get(PATH,user),409,"WORKING_CONDITIONS_DATA_INCOMPLETE");
    }
    @Test void ownStudentOnlyAndNoEmployerEndpointOrPermitStatusIsExposed() throws Exception {
        error(get(PATH,0),401,"UNAUTHORIZED");
        error(get(PATH,student("EMPLOYER")),403,"FORBIDDEN");
        long another=student("STUDENT"); error(get(PATH,another),403,"ACTIVE_BADGE_REQUIRED");
        var docs=mapper.readTree(get("/v3/api-docs",0).body());
        var op=docs.path("paths").path(PATH).path("get");
        assertThat(op.isMissingNode()).isFalse(); assertThat(op.has("requestBody")).isFalse();
        assertThat(docs.path("components").path("schemas").path("StudentWorkingConditionsResponse").path("properties").has("permitStatus")).isFalse();
        jdbc.update("update user_account set withdrawn_at=now() where id=?",user);
        error(get(PATH,user),403,"WITHDRAWN_ACCOUNT");
    }

    @Test void finalDocumentIssuesBadgeAndPersistsEstimateBeforeAnyReadRequest() throws Exception {
        jdbc.update("update student_profile set hirelink_badge_issued_at=null where id=?",user);
        jdbc.update("delete from student_enrollment_document where user_id=? and document_type='TOPIK'",user);
        var draft=academics.preview(user,"topik",pdf());
        assertThat(stored()).isNull();
        assertThat(jdbc.queryForObject("select hirelink_badge_issued_at from student_profile where id=?",java.sql.Timestamp.class,user)).isNull();

        academics.confirm(user,"topik",draft.draftId());

        assertThat(stored().path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(30);
        assertThat(stored().path("estimate").path("weekendsAndHolidays").path("type").asString()).isEqualTo("NO_TIME_CAP");
        assertThat(stored().path("basicInformation").path("topikLevel").asInt()).isEqualTo(5);
        assertThat(jdbc.queryForObject("select hirelink_badge_issued_at from student_profile where id=?",java.sql.Timestamp.class,user)).isNotNull();
        assertThat(profiles.findById(user).orElseThrow().getWorkingConditions()).containsKeys("basicInformation","estimate","calculatedOn","accreditationEffectiveFrom");
        assertThat(current()).isEqualTo(stored());
    }

    @Test void successfulReRegistrationStoresNewEstimateWithoutCallingReadApi() throws Exception {
        school("미인증대학교"); conditions.refreshExisting(user);
        var initial=stored();
        assertThat(initial.path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(25);
        var draft=academics.preview(user,"transcript",pdf());
        assertThat(stored()).isEqualTo(initial);
        academics.confirm(user,"transcript",draft.draftId());
        assertThat(stored().path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(30);

        var before=stored();
        when(academicOcr.recognize(eq("TRANSCRIPT"),any(),anyString())).thenThrow(AcademicExtraction.unreadable());
        assertThatThrownBy(()->academics.preview(user,"transcript",pdf())).isInstanceOf(StudentDocumentException.class);
        assertThat(stored()).isEqualTo(before);
    }

    @Test void snapshotWriteFailureRollsBackFinalDocumentProfileAndBadge() throws Exception {
        conditions.refreshExisting(user);
        var draft=academics.preview(user,"transcript",pdf());
        var before=jdbc.queryForMap("select * from student_profile where id=?",user);
        var docBefore=jdbc.queryForMap("select * from student_enrollment_document where user_id=? and document_type='TRANSCRIPT'",user);
        jdbc.execute("""
            CREATE FUNCTION fail_working_conditions_update() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'snapshot write failed'; END;
            $$ LANGUAGE plpgsql
            """);
        jdbc.execute("CREATE TRIGGER fail_working_conditions_update BEFORE UPDATE ON student_profile FOR EACH ROW EXECUTE FUNCTION fail_working_conditions_update()");
        try {
            assertThatThrownBy(()->academics.confirm(user,"transcript",draft.draftId())).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForMap("select * from student_profile where id=?",user)).isEqualTo(before);
            assertThat(jdbc.queryForMap("select * from student_enrollment_document where user_id=? and document_type='TRANSCRIPT'",user)).isEqualTo(docBefore);
            verify(storage,never()).delete("transcript/original");
        } finally {
            jdbc.execute("DROP TRIGGER fail_working_conditions_update ON student_profile");
            jdbc.execute("DROP FUNCTION fail_working_conditions_update()");
        }
    }

    @Test void schedulerBackfillsAndAppliesNewCatalogOnItsKoreanEffectiveDate() throws Exception {
        var scheduler=new StudentWorkingConditionsScheduler(profiles,conditions);
        assertThat(stored()).isNull();
        scheduler.refreshAll();
        assertThat(stored().path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(30);
        var badge=jdbc.queryForObject("select hirelink_badge_issued_at from student_profile where id=?",java.sql.Timestamp.class,user);
        var originalDocuments=jdbc.queryForList("select * from student_enrollment_document where user_id=?",user);
        LocalDate next=today.plusDays(1);
        catalog(next,"새인증대학교");
        scheduler.refreshAll();
        assertThat(stored().path("accreditationEffectiveFrom").asString()).isEqualTo(INITIAL.toString());
        clockDate(next);
        scheduler.refreshAll();
        assertThat(stored().path("accreditationEffectiveFrom").asString()).isEqualTo(next.toString());
        assertThat(stored().path("calculatedOn").asString()).isEqualTo(next.toString());
        assertThat(stored().path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(25);
        assertThat(stored().path("estimate").path("vacations").path("type").asString()).isEqualTo("NO_TIME_CAP");
        assertThat(jdbc.queryForObject("select hirelink_badge_issued_at from student_profile where id=?",java.sql.Timestamp.class,user)).isEqualTo(badge);
        assertThat(jdbc.queryForList("select * from student_enrollment_document where user_id=?",user)).isEqualTo(originalDocuments);
        verifyNoInteractions(academicOcr,enrollmentOcr,registrationOcr,apick,storage);
    }

    @Test void incompleteCatalogRetainsStoredValueAndDoesNotReturnItAsCurrent() throws Exception {
        conditions.refreshExisting(user); var before=stored();
        jdbc.update("update accredited_university_catalog set expected_entry_count=185 where effective_from=?",INITIAL);
        try {
            new StudentWorkingConditionsScheduler(profiles,conditions).refreshAll();
            assertThat(stored()).isEqualTo(before);
            error(get(PATH,user),503,"ACCREDITED_UNIVERSITY_LIST_UNAVAILABLE");
        } finally { jdbc.update("update accredited_university_catalog set expected_entry_count=184 where effective_from=?",INITIAL); }
    }

    @Test void storedEstimateDoesNotBypassBadgeAndRefreshStopsAtWithdrawalDeadline() throws Exception {
        conditions.refreshExisting(user); var before=stored();
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today.minusDays(1),user);
        conditions.refreshExisting(user);
        assertThat(stored()).isEqualTo(before);
        error(get(PATH,user),403,"ACTIVE_BADGE_REQUIRED");
        jdbc.update("update student_registration_verification set stay_expires_on=? where user_id=?",today.plusDays(30),user);
        Instant now=clock.instant();
        var requested=java.sql.Timestamp.from(now.minus(Duration.ofDays(1)));
        jdbc.update("update user_account set withdrawn_at=?,withdrawal_requested_at=? where id=?",requested,requested,user);
        school("미인증대학교"); conditions.refreshExisting(user);
        assertThat(stored().path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(25);
        error(get(PATH,user),403,"WITHDRAWN_ACCOUNT");
        var pending=stored();
        jdbc.update("update user_account set withdrawal_requested_at=? where id=?",java.sql.Timestamp.from(now.minus(Duration.ofDays(30))),user);
        school("숭실대학교"); conditions.refreshExisting(user);
        assertThat(stored()).isEqualTo(pending);
    }

    private void clockDate(LocalDate date) {
        var fixed=Clock.fixed(date.atTime(12,0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());
        when(clock.getZone()).thenReturn(fixed.getZone());
        when(clock.withZone(any())).thenAnswer(call->fixed.withZone(call.getArgument(0)));
    }
    private JsonNode stored() throws Exception {
        String json=jdbc.queryForObject("select working_conditions::text from student_profile where id=?",String.class,user);
        return json==null?null:mapper.readTree(json);
    }
    private Map<String,Object> profileWithoutEstimate() {
        var value=new HashMap<>(jdbc.queryForMap("select * from student_profile where id=?",user));
        value.remove("working_conditions"); return value;
    }
    private void catalog(LocalDate date,String school) {
        testCatalogs.add(date);
        jdbc.update("insert into accredited_university_catalog(effective_from,source_name,expected_entry_count) values (?,'test',1)",date);
        jdbc.update("insert into accredited_university_entry(effective_from,category,listed_name,normalized_name) values (?,'GENERAL',?,?)",date,school,AccreditedUniversityService.normalize(school));
    }
    private void ready(long id) {
        jdbc.update("update student_profile set visa_code='D-2-2',study_program_id=(select id from study_program where program_name='학사'),hirelink_badge_issued_at=now() where id=?",id);
        jdbc.update("insert into student_registration_verification(user_id,nationality,visa_code,stay_expires_on,card_verified_at,registered_at) values (?,'VN','D-2',?,now(),now())",id,today.plusDays(30));
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,university_name,campus_name,grade_level,registered_at) values (?,'ENROLLMENT','enrollment/original','숭실대학교','표시없음',3,now())",id);
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,average_gpa,academic_year,semester,registered_at) values (?,'TRANSCRIPT','transcript/original',3.99,2026,'FIRST',now())",id);
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,topik_level,registered_at) values (?,'TOPIK','topik/original',4,now())",id);
    }
    private void school(String name) { jdbc.update("update student_enrollment_document set university_name=? where user_id=? and document_type='ENROLLMENT'",name,user); }
    private void weekday(int hours) throws Exception { assertThat(current().path("estimate").path("termWeekdays").path("hours").asInt()).isEqualTo(hours); }
    private JsonNode current() throws Exception { var result=get(PATH,user); assertThat(result.statusCode()).as(result.body()).isEqualTo(200); assertThat(result.headers().firstValue("Cache-Control")).contains("no-store"); return mapper.readTree(result.body()); }
    private MockMultipartFile pdf() { return new MockMultipartFile("file","document.pdf","application/pdf","%PDF-test".getBytes(StandardCharsets.US_ASCII)); }
    private MockMultipartFile image() { return new MockMultipartFile("file","card.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,0}); }
    private long student(String role) {
        long id=jdbc.queryForObject("insert into user_account(email,role,name) values (?,?,'학생') returning id",Long.class,UUID.randomUUID()+"@example.com",role);
        jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level,onboarding_completed_at) values (?,'2000-01-01','VN','서울','BEGINNER',now())",id); return id;
    }
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
