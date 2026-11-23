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
    "auth.withdrawal.cleanup-initial-delay=3600000", "student-document.cleanup-initial-delay=3600000"})
class StudentWithdrawalIntegrationTest extends PostgreSQLIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired StudentWithdrawalService withdrawals;
    @Autowired StudentWithdrawalFinalizer finalizer;
    @Autowired SocialAuthService social;
    @Autowired TokenService tokens;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired ProviderTokenProtector protector;
    @MockitoBean ProviderUnlinkClient unlink;
    @MockitoBean StudentDocumentStorageClient storage;
    @LocalServerPort int port;
    private long user;
    private final Set<String> files=new HashSet<>();

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE user_account, social_auth_session, student_document_cleanup RESTART IDENTITY CASCADE");
        reset(unlink,storage); files.clear();
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
        assertThat(count("student_enrollment_document")).isZero(); assertThat(count("student_document_cleanup")).isZero();
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

    private void makeDue() { jdbc.update("update user_account set withdrawal_requested_at=now()-interval '30 days 1 second', withdrawn_at=now()-interval '30 days 1 second' where id=?",user); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }
    private HttpResponse<String> post(String token,String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/students/me/withdrawal"))
            .header("Authorization","Bearer "+token).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
