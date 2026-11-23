package com.hirelink.domain.member;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.service.*;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.member.service.*;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.global.exception.WithdrawnAccountException;
import com.hirelink.support.PostgreSQLIntegrationTest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "jwt.secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkwMTI=",
    "auth.withdrawal.enabled=true", "auth.withdrawal.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "auth.withdrawal.cleanup-initial-delay=3600000", "student-document.cleanup-initial-delay=3600000",
    "student-working-conditions.refresh-enabled=false"})
class StudentWithdrawalIntegrationTest extends PostgreSQLIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired StudentWithdrawalService withdrawals;
    @Autowired StudentWithdrawalFinalizer finalizer;
    @Autowired StudentWithdrawalRetentionService retention;
    @Autowired StudentWithdrawalScheduler scheduler;
    @Autowired com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository documents;
    @Autowired com.hirelink.domain.student.service.StudentEnrollmentService enrollments;
    @Autowired com.hirelink.domain.student.service.StudentDocumentCompletionService completion;
    @Autowired com.hirelink.domain.student.service.StudentBadgeService badges;
    @Autowired com.hirelink.domain.student.service.StudentWorkingConditionsService conditions;
    @Autowired SocialAuthService social;
    @Autowired SocialSignupTermsService signupTerms;
    @Autowired SocialSignupPhoneService signupPhone;
    @Autowired com.hirelink.domain.auth.repository.PhoneVerificationRepository phoneVerifications;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired TokenService tokens;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired ProviderTokenProtector protector;
    @MockitoBean ProviderUnlinkClient unlink;
    @MockitoBean StudentDocumentStorageClient storage;
    @MockitoBean Clock clock;
    @LocalServerPort int port;
    private long user;
    private final Set<String> files=new HashSet<>();

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE user_account, social_auth_session, student_document_cleanup RESTART IDENTITY CASCADE");
        reset(unlink,storage); files.clear();
        at(Instant.now());
        user=jdbc.queryForObject("insert into user_account(role,email,name) values ('STUDENT','student@example.com','학생') returning id",Long.class);
        jdbc.update("insert into auth_account(user_id,provider,provider_user_id) values (?,'GOOGLE','student-subject')",user);
        jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level) values (?,'2000-01-01','VN','서울','BEGINNER')",user);
        String key="topik/"+user+"/"+UUID.randomUUID(); files.add(key);
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,topik_level,registered_at) values (?,'TOPIK',?,4,now())",user,key);
        doAnswer(call->{files.remove(call.getArgument(0)); return null;}).when(storage).delete(anyString());
    }

    @Test void withdrawalRetainsOriginalsAndIdentityButRevokesTokensAndFreshLoginRestores() {
        var before=Set.copyOf(files);
        var old=tokens.issueTokens(users.findById(user).orElseThrow());
        var otherDevice=tokens.issueTokens(users.findById(user).orElseThrow());
        String staleSession=social.createSession(AuthProvider.GOOGLE,"student-subject",MemberRole.STUDENT,null);
        var result=withdrawals.request(user);
        assertThat(Duration.between(result.requestedAt(),result.recoveryDeadline())).isEqualTo(Duration.ofDays(30));
        assertThat(count("refresh_token")).isZero();
        assertThat(count("auth_account")).isEqualTo(1); assertThat(files).isEqualTo(before);
        verify(unlink,never()).unlink(any()); verifyNoInteractions(storage);
        assertThatThrownBy(()->jwtDecoder.decode(old.accessToken())).isInstanceOf(JwtException.class);
        assertThatThrownBy(()->jwtDecoder.decode(otherDevice.accessToken())).isInstanceOf(JwtException.class);
        assertThatThrownBy(()->social.getResult(staleSession)).isInstanceOf(WithdrawnAccountException.class);
        assertThat(users.findById(user).orElseThrow().isWithdrawn()).isTrue();
        String fresh=social.createSession(AuthProvider.GOOGLE,"student-subject",MemberRole.STUDENT,null,"provider-refresh");
        String encrypted=jdbc.queryForObject("select revocation_token from auth_account where user_id=?",String.class,user);
        assertThat(encrypted).doesNotContain("provider-refresh");
        assertThat(protector.decrypt(encrypted,"GOOGLE:student-subject")).isEqualTo("provider-refresh");
        var restored=social.getResult(fresh);
        assertThat(users.findById(user).orElseThrow().isWithdrawn()).isFalse();
        assertThat(((Number)jwtDecoder.decode(restored.tokens().accessToken()).getClaim("account_version")).longValue()).isEqualTo(1L);
        assertThatThrownBy(()->jwtDecoder.decode(old.accessToken())).isInstanceOf(JwtException.class);
        assertThat(files).isEqualTo(before); finalizer.finalizeDue(user); assertThat(files).isEqualTo(before);
    }

    @Test void ordinaryLogoutPreservesDocumentsProfileAndSocialIdentity() {
        var first=tokens.issueTokens(users.findById(user).orElseThrow());
        var second=tokens.issueTokens(users.findById(user).orElseThrow());
        var beforeFiles=Set.copyOf(files);
        var beforeAccount=jdbc.queryForList("select * from user_account");
        var beforeIdentity=jdbc.queryForList("select * from auth_account");
        var beforeProfile=jdbc.queryForList("select * from student_profile");
        var beforeDocuments=jdbc.queryForList("select * from student_enrollment_document");
        tokens.logout(first.refreshToken());
        assertThatThrownBy(()->jwtDecoder.decode(first.accessToken())).isInstanceOf(JwtException.class);
        assertThat(jwtDecoder.decode(second.accessToken()).getSubject()).isEqualTo(Long.toString(user));
        assertThat(count("refresh_token")).isEqualTo(1);
        assertThat(jdbc.queryForList("select * from user_account")).isEqualTo(beforeAccount);
        assertThat(jdbc.queryForList("select * from auth_account")).isEqualTo(beforeIdentity);
        assertThat(jdbc.queryForList("select * from student_profile")).isEqualTo(beforeProfile);
        assertThat(jdbc.queryForList("select * from student_enrollment_document")).isEqualTo(beforeDocuments);
        assertThat(files).isEqualTo(beforeFiles);
        verifyNoInteractions(storage,unlink);
    }

    @Test void thirtyDayDeadlineBlocksRestorationEvenBeforeCleanupAndPhysicallyDeletesAllKinds() {
        withdrawals.request(user);
        finalizer.finalizeDue(user); assertThat(files).hasSize(1);
        makeDue();
        assertThatThrownBy(()->social.createSession(AuthProvider.GOOGLE,"student-subject",MemberRole.STUDENT,null)).isInstanceOf(WithdrawnAccountException.class);
        for(String kind:List.of("enrollment","transcript")) {
            String key=kind+"/"+user+"/"+UUID.randomUUID(); files.add(key);
            jdbc.update("insert into student_enrollment_document(user_id,document_type,draft_id,draft_object_key) values (?,?,?,?)",user,kind.toUpperCase(Locale.ROOT),UUID.randomUUID(),key);
        }
        String abandoned="topik/"+user+"/"+UUID.randomUUID(); files.add(abandoned);
        jdbc.update("insert into student_document_cleanup(object_key,available_at) values (?,now())",abandoned);
        finalizer.finalizeDue(user);
        assertThat(files).isEmpty(); assertThat(count("auth_account")).isZero();
        assertThat(count("student_enrollment_document")).isEqualTo(1); assertThat(count("student_document_cleanup")).isZero();
        assertThat(documents.findAllByUserId(user)).isEmpty();
        assertThat(users.findById(user).orElseThrow().getWithdrawalFinalizedAt()).isNotNull();
        verify(unlink).unlink(argThat(account->account.getProvider()==AuthProvider.GOOGLE));
        finalizer.finalizeDue(user); verify(unlink,times(1)).unlink(any());
        String signup=social.createSession(AuthProvider.GOOGLE,"student-subject",MemberRole.STUDENT,null);
        assertThat(social.getResult(signup).response().status().name()).isEqualTo("SIGNUP_REQUIRED");
    }

    @Test void externalFailureDoesNotFinalizeOrPermitRestorationAndCanBeRetried() {
        withdrawals.request(user); makeDue();
        doThrow(new IllegalStateException("temporary storage failure")).when(storage).delete(anyString());
        assertThatThrownBy(()->finalizer.finalizeDue(user)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(user).orElseThrow().getWithdrawalFinalizedAt()).isNull();
        verify(unlink,never()).unlink(any()); assertThat(count("student_enrollment_document")).isEqualTo(1);
        doAnswer(call->{files.remove(call.getArgument(0)); return null;}).when(storage).delete(anyString());
        doThrow(new IllegalStateException("temporary provider failure")).when(unlink).unlink(any());
        assertThatThrownBy(()->finalizer.finalizeDue(user)).isInstanceOf(IllegalStateException.class);
        assertThat(count("auth_account")).isEqualTo(1);
        assertThat(users.findById(user).orElseThrow().getWithdrawalFinalizedAt()).isNull();
        doNothing().when(unlink).unlink(any()); finalizer.finalizeDue(user);
        assertThat(count("auth_account")).isZero(); assertThat(files).isEmpty();
    }

    @Test void endpointRequiresExplicitConfirmationAndInvalidatesItsBearerAfterSuccess() throws Exception {
        String token=tokens.issueTokens(users.findById(user).orElseThrow()).accessToken();
        for(String body:List.of("{}","{\"confirmed\":false}","{\"confirmed\":null}")) assertThat(post(token,body).statusCode()).isEqualTo(400);
        assertThat(users.findById(user).orElseThrow().isWithdrawn()).isFalse();
        var response=post(token,"{\"confirmed\":true}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().allValues("Set-Cookie")).anyMatch(value->value.startsWith("refreshToken=") && value.contains("Max-Age=0"));
        assertThat(post(token,"{\"confirmed\":true}").statusCode()).isEqualTo(401);
    }

    @Test void exactDeadlineAndLegacyWithdrawalCannotRestore() {
        var account=users.findById(user).orElseThrow(); Instant requested=Instant.parse("2026-01-01T00:00:00Z");
        account.requestWithdrawal(requested);
        assertThat(account.canRestore(requested.plus(Duration.ofDays(30)).minusNanos(1))).isTrue();
        assertThat(account.canRestore(requested.plus(Duration.ofDays(30)))).isFalse();
        jdbc.update("update user_account set withdrawn_at=now() where id=?",user);
        assertThatThrownBy(()->social.createSession(AuthProvider.GOOGLE,"student-subject",MemberRole.STUDENT,null)).isInstanceOf(WithdrawnAccountException.class);
    }

    @Test void finalWithdrawalRetainsConfirmedValuesAndContactButHidesThemFromServiceReads() {
        seedRetainedRecords();
        var accountBefore=jdbc.queryForList("select name,email,phone_number,phone_verified_at,created_at from user_account where id=?",user);
        var profileBefore=jdbc.queryForList("select * from student_profile where id=?",user);
        var confirmedBefore=jdbc.queryForList("select document_type,registered_at,university_name,campus_name,grade_level,average_gpa,topik_level,academic_year,semester from student_enrollment_document order by document_type");
        withdrawals.request(user);
        var requested=users.findById(user).orElseThrow().getWithdrawalRequestedAt();
        at(requested.plus(Duration.ofDays(30)).minusNanos(1)); finalizer.finalizeDue(user);
        assertThat(users.findById(user).orElseThrow().getWithdrawalFinalizedAt()).isNull();
        at(requested.plus(Duration.ofDays(30))); finalizer.finalizeDue(user);
        assertThat(files).isEmpty();
        assertThat(jdbc.queryForList("select name,email,phone_number,phone_verified_at,created_at from user_account where id=?",user)).isEqualTo(accountBefore);
        assertThat(jdbc.queryForList("select * from student_profile where id=?",user)).isEqualTo(profileBefore);
        assertThat(jdbc.queryForList("select document_type,registered_at,university_name,campus_name,grade_level,average_gpa,topik_level,academic_year,semester from student_enrollment_document order by document_type")).isEqualTo(confirmedBefore);
        assertThat(jdbc.queryForObject("select count(*) from student_enrollment_document where deleted_at is not null and object_key is null and draft_object_key is null and draft_id is null",Integer.class)).isEqualTo(3);
        assertThat(count("phone_verification")).isZero();
        assertThat(jdbc.queryForObject("select renewal_payload from student_registration_verification where user_id=?",String.class,user)).isNull();
        assertThat(documents.findAllByUserId(user)).isEmpty();
        assertThat(documents.findById(user)).isEmpty();
        var archived=users.findById(user).orElseThrow();
        assertThat(archived.getName()).isNull(); assertThat(archived.getEmail()).isNull(); assertThat(archived.getPhoneNumber()).isNull();
        assertThat(archived.isPhoneVerified()).isFalse();
        assertThatThrownBy(()->enrollments.current(user)).isInstanceOf(WithdrawnAccountException.class);
        assertThatThrownBy(()->completion.current(user)).isInstanceOf(WithdrawnAccountException.class);
        assertThatThrownBy(()->badges.current(user)).isInstanceOf(WithdrawnAccountException.class);
        assertThatThrownBy(()->conditions.current(user)).isInstanceOf(WithdrawnAccountException.class);
    }

    @Test void purgesAtThreeYearDeadlineAndPreservesOtherUsersAndRelationshipIds() {
        seedRetainedRecords(); withdrawals.request(user); makeDue(); finalizer.finalizeDue(user);
        long other=jdbc.queryForObject("insert into user_account(role,email,phone_number) values ('STUDENT','other@example.com','01000000001') returning id",Long.class);
        jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level) values (?,'2001-01-01','VN','서울','BEGINNER')",other);
        jdbc.update("insert into student_career(user_id,career_name,start_month,end_month) values (?,'다른 경력','2026-01','2026-02')",other);
        jdbc.update("insert into recommendation_batch(student_profile_id,algorithm_version,input_snapshot,created_at) values (?,'v1','{}',now())",user);
        var otherBefore=jdbc.queryForList("select * from user_account where id=?",other);
        var relatedBefore=jdbc.queryForList("select * from recommendation_batch");
        Instant deadline=users.findById(user).orElseThrow().getWithdrawalRetainUntil();
        var records=jdbc.queryForList("select * from student_enrollment_document");
        at(deadline.minusNanos(1000)); retention.purgeDue(user);
        assertThat(jdbc.queryForList("select * from student_enrollment_document")).isEqualTo(records);
        assertThat(users.findById(user).orElseThrow().getRetainedDataPurgedAt()).isNull();
        at(deadline); scheduler.cleanup();
        assertThat(users.findById(user).orElseThrow().getRetainedDataPurgedAt()).isEqualTo(deadline);
        for(String table:List.of("student_enrollment_document","student_registration_verification","student_career","student_preferred_job",
                "student_preferred_work_time","student_preferred_location","student_priority","student_job_preference","user_term_agreement","phone_verification_send_attempt")) {
            assertThat(jdbc.queryForObject("select count(*) from "+table+" where user_id=?",Integer.class,user)).as(table).isZero();
        }
        var member=jdbc.queryForMap("select name,email,phone_number,phone_verified_at,email_verified_at,preferred_language,acquisition_channel,created_at from user_account where id=?",user);
        assertThat(member.values()).containsOnlyNulls();
        var profile=jdbc.queryForMap("select * from student_profile where id=?",user);
        profile.remove("id"); profile.remove("retained_data_purged_at");
        assertThat(profile.values()).containsOnlyNulls();
        assertThat(jdbc.queryForList("select * from recommendation_batch")).isEqualTo(relatedBefore);
        assertThat(jdbc.queryForList("select * from user_account where id=?",other)).isEqualTo(otherBefore);
        assertThat(jdbc.queryForObject("select count(*) from student_career where user_id=?",Integer.class,other)).isEqualTo(1);
        var purged=jdbc.queryForList("select * from user_account where id=?",user);
        at(deadline.plusSeconds(60)); scheduler.cleanup(); retention.purgeDue(user);
        assertThat(jdbc.queryForList("select * from user_account where id=?",user)).isEqualTo(purged);
    }

    @Test void retentionCleanupFailureRollsBackEveryTableAndCanBeRetried() {
        seedRetainedRecords(); withdrawals.request(user); makeDue(); finalizer.finalizeDue(user);
        at(users.findById(user).orElseThrow().getWithdrawalRetainUntil());
        var before=jdbc.queryForList("select * from student_enrollment_document");
        jdbc.execute("alter table user_account add constraint ck_test_purge_failure check (retained_data_purged_at is null)");
        try {
            assertThatThrownBy(()->retention.purgeDue(user)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForList("select * from student_enrollment_document")).isEqualTo(before);
            assertThat(count("student_career")).isEqualTo(1);
            assertThat(users.findById(user).orElseThrow().getRetainedDataPurgedAt()).isNull();
        } finally { jdbc.execute("alter table user_account drop constraint ck_test_purge_failure"); }
        retention.purgeDue(user);
        assertThat(count("student_enrollment_document")).isZero();
        assertThat(users.findById(user).orElseThrow().getRetainedDataPurgedAt()).isNotNull();
    }

    @Test void phoneUniquenessIncludesDormantAccountsButAllowsRejoiningAfterFinalWithdrawal() {
        seedRetainedRecords(); withdrawals.request(user);
        assertThat(users.existsByPhoneNumberAndWithdrawalFinalizedAtIsNull("01000000001")).isTrue();
        assertThat(users.existsByPhoneNumberAndIdNotAndWithdrawalFinalizedAtIsNull("01000000001",user)).isFalse();
        assertThatThrownBy(()->jdbc.update("insert into user_account(role,phone_number) values ('STUDENT','01000000001')")).isInstanceOf(DataIntegrityViolationException.class);
        makeDue(); finalizer.finalizeDue(user);
        assertThat(users.existsByPhoneNumberAndWithdrawalFinalizedAtIsNull("01000000001")).isFalse();
        long newId=jdbc.queryForObject("insert into user_account(role,email,phone_number) values ('STUDENT','student@example.com','01000000001') returning id",Long.class);
        assertThat(newId).isNotEqualTo(user);
        assertThat(users.existsByPhoneNumberAndIdNotAndWithdrawalFinalizedAtIsNull("01000000001",user)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from user_account where phone_number='01000000001'",Integer.class)).isEqualTo(2);
        assertThatThrownBy(()->jdbc.update("insert into user_account(role,phone_number) values ('STUDENT','01000000001')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from student_profile where id=?",Integer.class,newId)).isZero();
    }

    @Test void calendarYearsUseKoreaTimeAndHandleLeapDay() {
        var account=users.findById(user).orElseThrow();
        Instant finalized=ZonedDateTime.of(2028,2,29,15,0,0,0,ZoneId.of("Asia/Seoul")).toInstant();
        account.finalizeWithdrawal(finalized);
        Instant expected=ZonedDateTime.of(2031,2,28,15,0,0,0,ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(account.getWithdrawalRetainUntil()).isEqualTo(expected);
        assertThat(account.isRetentionExpired(expected.minusNanos(1))).isFalse();
        assertThat(account.isRetentionExpired(expected)).isTrue();
        assertThatThrownBy(()->account.purgeRetainedInformation(expected.minusNanos(1))).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejoiningWithSameSocialIdentityAndPhoneCreatesNewAccountAfterFreshConsentAndVerification() {
        seedRetainedRecords(); withdrawals.request(user); makeDue(); finalizer.finalizeDue(user);
        String signup=social.createSession(AuthProvider.GOOGLE,"student-subject",MemberRole.STUDENT,"student@example.com");
        assertThat(social.getResult(signup).response().status().name()).isEqualTo("SIGNUP_REQUIRED");
        assertThat(signupTerms.getTerms(signup).terms()).allMatch(term -> !term.isAgreed());
        assertThatThrownBy(()->signupPhone.confirmCode(signup,"01000000001","123456"))
                .isInstanceOf(com.hirelink.global.exception.SocialAuthException.class);
        signupTerms.saveTerms(signup,new com.hirelink.domain.auth.dto.SocialSignupTermsRequest(
                signupTerms.getTerms(signup).terms().stream().map(term -> new com.hirelink.domain.auth.dto.SocialSignupTermsRequest.Agreement(
                        term.termTypeId(),term.isRequired(),term.currentVersion())).toList()));
        assertThatThrownBy(()->signupPhone.confirmCode(signup,"01000000001","123456"))
                .isInstanceOf(com.hirelink.global.exception.InvalidPhoneVerificationCodeException.class);
        String hash=jdbc.queryForObject("select token_hash from social_auth_session",String.class);
        phoneVerifications.saveAndFlush(com.hirelink.domain.auth.entity.PhoneVerification.forSignup(
                hash,"01000000001",passwordEncoder.encode("123456"),Instant.now().plusSeconds(300)));
        var login=signupPhone.confirmCode(signup,"01000000001","123456").orElseThrow();
        long newId=Long.parseLong(jwtDecoder.decode(login.accessToken()).getSubject());
        assertThat(newId).isNotEqualTo(user);
        assertThat(users.findById(newId).orElseThrow().isWithdrawn()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from user_account where email='student@example.com' and phone_number='01000000001'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select user_id from auth_account where provider='GOOGLE' and provider_user_id='student-subject'",Long.class)).isEqualTo(newId);
        for(String table:List.of("student_profile","student_enrollment_document","student_registration_verification","student_career","student_priority")) {
            String key=table.equals("student_profile") ? "id" : "user_id";
            assertThat(jdbc.queryForObject("select count(*) from "+table+" where "+key+"=?",Integer.class,newId)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from user_term_agreement where user_id=?",Integer.class,newId)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from student_enrollment_document where user_id=? and deleted_at is not null",Integer.class,user)).isEqualTo(3);
    }

    @Test void openApiExplainsRetentionWithoutChangingWithdrawalRequestOrResponse() throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var json=new tools.jackson.databind.ObjectMapper().readTree(response.body());
        var operation=json.path("paths").path("/api/students/me/withdrawal").path("post");
        assertThat(operation.path("description").asString()).contains("3년", "원본", "30일", "새 계정");
        assertThat(operation.path("responses").has("200")).isTrue();
    }

    private void at(Instant time) { when(clock.instant()).thenReturn(time); when(clock.getZone()).thenReturn(ZoneOffset.UTC); }

    private void seedRetainedRecords() {
        jdbc.update("update user_account set phone_number='01000000001',phone_verified_at=now(),email_verified_at=now(),acquisition_channel='SEARCH' where id=?",user);
        jdbc.update("update student_profile set university_name='숭실대학교',grade_level=3,previous_semester_gpa=4.24,topik_level=4,working_conditions='{}',hirelink_badge_issued_at=now(),onboarding_completed_at=now() where id=?",user);
        for(String kind:List.of("enrollment","transcript")) {
            String key=kind+"/"+user+"/"+UUID.randomUUID(); files.add(key);
            jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,registered_at,university_name,campus_name,grade_level,average_gpa,academic_year,semester) values (?,?,?,now(),'숭실대학교','서울',3,4.24,2026,'FIRST')",user,kind.toUpperCase(Locale.ROOT),key);
        }
        String draft="topik/"+user+"/"+UUID.randomUUID(); files.add(draft);
        jdbc.update("update student_enrollment_document set draft_id=?,draft_object_key=?,draft_topik_level=6 where user_id=? and document_type='TOPIK'",UUID.randomUUID(),draft,user);
        jdbc.update("insert into student_registration_verification(user_id,nationality,visa_code,stay_expires_on,card_verified_at,registered_at,renewal_id,renewal_expires_at,renewal_payload) values (?,'VN','D-2','2035-01-01',now(),now(),?,now()+interval '30 minutes','temporary')",user,UUID.randomUUID());
        jdbc.update("insert into student_career(user_id,career_name,start_month,end_month) values (?,'카페','2026-01','2026-02')",user);
        jdbc.update("insert into student_preferred_job(user_id,business_type_id) select ?,id from business_type order by id limit 1",user);
        jdbc.update("insert into student_preferred_work_time(user_id,day_of_week,start_time,end_time) values (?,'MON','22:00','02:00')",user);
        jdbc.update("insert into student_preferred_location(user_id,location_name,latitude,longitude,radius_m) values (?,'서울',37,127,500)",user);
        jdbc.update("insert into student_job_preference(user_id,business_type_flexible) values (?,true)",user);
        jdbc.update("insert into student_priority(user_id,criterion_id,priority_order) select ?,id,1 from priority_criterion order by id limit 1",user);
        jdbc.update("insert into user_term_agreement(user_id,term_type_id,is_agreed,agreed_at,term_version) select ?,id,true,now(),'v1' from term_type order by id limit 1",user);
        jdbc.update("insert into phone_verification(user_id,phone_number,code_hash,expires_at) values (?,'01000000001','hash',now()+interval '5 minutes')",user);
        jdbc.update("insert into phone_verification_send_attempt(user_id,phone_number,requested_at) values (?,'01000000001',now())",user);
    }

    private void makeDue() {
        var requested=java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(30)).minusSeconds(1));
        jdbc.update("update user_account set withdrawal_requested_at=?,withdrawn_at=? where id=?",requested,requested,user);
    }
    private int count(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }
    private HttpResponse<String> post(String token,String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/students/me/withdrawal"))
            .header("Authorization","Bearer "+token).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
