package com.hirelink.domain.employer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.hirelink.domain.auth.service.TokenService;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.employer.service.EmployerOnboardingService;
import com.hirelink.domain.workplace.client.NtsBusinessRegistrationClient;
import com.hirelink.domain.workplace.client.PaddleOcrClient;
import com.hirelink.domain.workplace.dto.BusinessRegistrationOcrResponse;
import com.hirelink.global.exception.BusinessRegistrationLookupException;
import com.hirelink.global.exception.UnreadableBusinessRegistrationException;
import com.hirelink.support.PostgreSQLIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmployerOnboardingIntegrationTest extends PostgreSQLIntegrationTest {
    private static final String PATH = "/api/employers/me/onboarding";
    private static final ObjectMapper JSON = new ObjectMapper();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired TokenService tokens;
    @Autowired EmployerOnboardingService service;
    @MockitoBean PaddleOcrClient paddle;
    @MockitoBean NtsBusinessRegistrationClient nts;
    private final HttpClient client = HttpClient.newHttpClient();
    private Long userId;
    private String token;

    @BeforeEach void prepare() {
        jdbc.execute("truncate table user_account, social_auth_session restart identity cascade");
        UserAccount user = new UserAccount("employer@example.com", MemberRole.EMPLOYER);
        user.verifyPhoneNumber("01000000000", Instant.now());
        users.saveAndFlush(user);
        userId = user.getId();
        token = tokens.issueTokens(user).accessToken();
        when(paddle.recognize(any(), anyString())).thenReturn(new BusinessRegistrationOcrResponse("123-45-67890", null));
        when(nts.isActiveBusiness(anyString())).thenReturn(true);
    }

    @Test void finalSaveRequiresReceiptAndVerifiedFinalNumberAndPreservesAccount() throws Exception {
        var before = jdbc.queryForMap("select * from user_account where id=?", userId);
        assertThat(body(get(PATH)).path("nextStep").asString()).isEqualTo("BASIC_INFORMATION");
        String attempt = restart();
        assertError(post(PATH, attempt, payload("1234567890")), 409, "BUSINESS_DOCUMENT_REQUIRED");
        assertError(verifyBusiness(attempt, "1234567890"), 409, "BUSINESS_DOCUMENT_REQUIRED");
        assertThat(ocr(attempt).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForMap("select * from user_account where id=?", userId)).isEqualTo(before);
        assertThat(count("employer_profile")).isZero();
        assertError(post(PATH, attempt, payload("1234567890")), 409, "BUSINESS_VERIFICATION_REQUIRED");
        assertThat(verifyBusiness(attempt, "0123456789").statusCode()).isEqualTo(200);
        assertError(post(PATH, attempt, payload("1234567890")), 409, "BUSINESS_VERIFICATION_REQUIRED");
        var response = post(PATH, attempt, payload("0123456789"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        verify(nts, times(2)).isActiveBusiness("0123456789");
        assertThat(count("user_account")).isEqualTo(1);
        assertThat(count("employer_profile")).isEqualTo(1);
        assertThat(count("workplace")).isEqualTo(1);
        assertThat(count("employer_onboarding_attempt")).isZero();
        assertThat(jdbc.queryForMap("select name, acquisition_channel, phone_number from user_account where id=?", userId))
                .containsEntry("name", "사업주").containsEntry("acquisition_channel", "SEARCH").containsEntry("phone_number", "01000000000");
        assertThat(jdbc.queryForMap("select workplace_address, workplace_base_address, workplace_address_detail, business_registration_file_key from workplace"))
                .containsEntry("workplace_address", "서울시 테스트로 10 2층")
                .containsEntry("workplace_base_address", "서울시 테스트로 10")
                .containsEntry("workplace_address_detail", "2층").containsEntry("business_registration_file_key", null);
        assertThat(body(get(PATH)).path("nextStep").asString()).isEqualTo("DASHBOARD");
        assertError(post(PATH, attempt, payload("0123456789")), 409, "EMPLOYER_ONBOARDING_COMPLETED");
        assertError(post(PATH + "/restart", null, Map.of()), 409, "EMPLOYER_ONBOARDING_COMPLETED");
    }

    @Test void reentryAndNewLoginCannotReuseOldProofButKeepMemberAndPhone() throws Exception {
        var before = jdbc.queryForMap("select * from user_account where id=?", userId);
        String old = ready();
        String fresh = restart();
        assertThat(fresh).isNotEqualTo(old);
        assertError(post(PATH, old, payload("1234567890")), 409, "EMPLOYER_ONBOARDING_RESTART_REQUIRED");
        assertError(post(PATH, fresh, payload("1234567890")), 409, "BUSINESS_DOCUMENT_REQUIRED");
        String priorLogin = token;
        token = tokens.issueTokens(users.findById(userId).orElseThrow()).accessToken();
        assertError(ocr(fresh), 409, "EMPLOYER_ONBOARDING_RESTART_REQUIRED");
        assertThat(body(get(PATH)).path("nextStep").asString()).isEqualTo("BASIC_INFORMATION");
        String current = restart();
        token = priorLogin;
        assertError(ocr(current), 409, "EMPLOYER_ONBOARDING_RESTART_REQUIRED");
        assertThat(jdbc.queryForMap("select * from user_account where id=?", userId)).isEqualTo(before);
        assertThat(count("employer_profile")).isZero();
    }

    @Test void expiredAttemptNeverDeletesAccountOrPermitsFinalSave() throws Exception {
        String attempt = ready();
        jdbc.update("update employer_onboarding_attempt set expires_at=now()-interval '1 second'");
        assertError(post(PATH, attempt, payload("1234567890")), 409, "EMPLOYER_ONBOARDING_RESTART_REQUIRED");
        service.deleteExpiredAttempts();
        assertThat(count("employer_onboarding_attempt")).isZero();
        assertThat(count("user_account")).isEqualTo(1);
        assertThat(body(get(PATH)).path("status").asString()).isEqualTo("REQUIRED");
    }

    @Test void failedReuploadOrReverificationInvalidatesEarlierProof() throws Exception {
        String attempt = ready();
        when(paddle.recognize(any(), anyString())).thenThrow(new UnreadableBusinessRegistrationException());
        assertError(ocr(attempt), 422, "UNREADABLE_BUSINESS_REGISTRATION");
        assertError(post(PATH, attempt, payload("1234567890")), 409, "BUSINESS_DOCUMENT_REQUIRED");
        when(paddle.recognize(any(), anyString())).thenReturn(new BusinessRegistrationOcrResponse("123-45-67890", null));
        assertThat(ocr(attempt).statusCode()).isEqualTo(200);
        assertThat(verifyBusiness(attempt, "1234567890").statusCode()).isEqualTo(200);
        when(nts.isActiveBusiness(anyString())).thenThrow(new BusinessRegistrationLookupException());
        assertError(verifyBusiness(attempt, "1234567890"), 502, "BUSINESS_REGISTRATION_LOOKUP_FAILED");
        assertError(post(PATH, attempt, payload("1234567890")), 409, "BUSINESS_VERIFICATION_REQUIRED");
    }

    @Test void finalNtsFailureRollsBackProfileButKeepsExistingMemberAndReceipt() throws Exception {
        String attempt = ready();
        var before = jdbc.queryForMap("select * from user_account where id=?", userId);
        when(nts.isActiveBusiness("1234567890")).thenReturn(false);
        assertThat(post(PATH, attempt, payload("1234567890")).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForMap("select * from user_account where id=?", userId)).isEqualTo(before);
        assertThat(count("employer_profile")).isZero();
        assertThat(count("workplace")).isZero();
        assertThat(count("employer_onboarding_attempt")).isEqualTo(1);
        when(nts.isActiveBusiness("1234567890")).thenReturn(true);
        assertThat(post(PATH, attempt, payload("1234567890")).statusCode()).isEqualTo(201);
    }

    @Test void concurrentFinalSubmitCreatesOnlyOneWorkplace() throws Exception {
        String attempt = ready();
        var request = jsonRequest(PATH, attempt, payload("1234567890"));
        var one = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var two = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(one.get(20, TimeUnit.SECONDS).statusCode(), two.get(20, TimeUnit.SECONDS).statusCode()))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(count("workplace")).isEqualTo(1);
    }

    @Test void studentsAndUnauthenticatedUsersCannotUseEmployerFlow() throws Exception {
        String attempt = restart();
        assertThat(client.send(HttpRequest.newBuilder(uri(PATH)).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        UserAccount student = users.saveAndFlush(new UserAccount("student@example.com", MemberRole.STUDENT));
        token = tokens.issueTokens(student).accessToken();
        assertThat(get(PATH).statusCode()).isEqualTo(403);
        assertThat(post(PATH + "/restart", null, Map.of()).statusCode()).isEqualTo(403);
        assertThat(ocr(attempt).statusCode()).isEqualTo(403);
        assertThat(count("employer_profile")).isZero();
        assertThat(count("user_account")).isEqualTo(2);
    }

    @Test void fullAddressRemainsAvailableAndDetailOnlyEditsKeepCoordinates() throws Exception {
        String attempt = ready();
        long workplace = body(post(PATH, attempt, payload("1234567890"))).path("workplaceId").asLong();
        var detail = body(get("/api/workplaces/" + workplace));
        assertThat(detail.path("workplaceAddress").asString()).isEqualTo("서울시 테스트로 10");
        assertThat(detail.path("workplaceAddressDetail").asString()).isEqualTo("2층");
        var update = client.send(builder("/api/workplaces/" + workplace, null)
                .header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers.ofString("{\"workplaceAddressDetail\":\"3층\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(update.statusCode()).as(update.body()).isEqualTo(200);
        assertThat(body(update).path("latitude")).isEqualTo(detail.path("latitude"));
        assertThat(jdbc.queryForObject("select workplace_address from workplace where id=?", String.class, workplace)).isEqualTo("서울시 테스트로 10 3층");
    }

    @Test void migrationPreservesExistingAddressesAndMarksOnlyPreviouslyCompleteEmployers() throws Exception {
        jdbc.update("insert into employer_profile(id, position) values (?,?)", userId, "대표");
        var old = post("/api/workplaces", null, payload("1234567890").get("workplace"));
        assertThat(old.statusCode()).as(old.body()).isEqualTo(201);
        jdbc.update("update workplace set workplace_address='기존 전체 주소 502호', workplace_base_address=null, workplace_address_detail=null");
        var before = jdbc.queryForMap("select * from workplace");
        var partial = users.saveAndFlush(new UserAccount("partial@example.com", MemberRole.EMPLOYER));
        jdbc.update("insert into employer_profile(id) values (?)", partial.getId());
        String migration;
        try (var input = getClass().getResourceAsStream("/db/migration/V44__employer_onboarding_and_split_address.sql")) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = migration.indexOf("UPDATE employer_profile");
        jdbc.execute(migration.substring(start, migration.indexOf(';', start) + 1));
        assertThat(body(get(PATH)).path("status").asString()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForMap("select * from workplace")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select onboarding_completed_at from employer_profile where id=?", java.sql.Timestamp.class, partial.getId())).isNull();
        var detail = body(get("/api/workplaces/" + body(old).path("workplaceId").asLong()));
        assertThat(detail.path("workplaceAddress").asString()).isEqualTo("기존 전체 주소 502호");
        assertThat(detail.path("workplaceAddressDetail").isNull()).isTrue();
    }

    @Test void openApiDescribesBearerAttemptAndAtomicSave() throws Exception {
        var doc = body(get("/v3/api-docs"));
        var operation = doc.path("paths").path(PATH).path("post");
        assertThat(operation.path("responses").has("201")).isTrue();
        assertThat(operation.path("responses").has("409")).isTrue();
        assertThat(operation.path("description").asString()).contains("한 트랜잭션", "국세청 상태를 다시 조회");
        assertThat(operation.path("parameters").toString()).contains("X-Onboarding-Attempt");
        assertThat(doc.path("components").path("schemas").path("WorkplaceDetailResponse").path("properties").has("workplaceAddressDetail")).isTrue();
    }

    private String ready() throws Exception {
        String attempt = restart();
        var ocr = ocr(attempt); assertThat(ocr.statusCode()).as(ocr.body()).isEqualTo(200);
        assertThat(body(ocr).propertyNames()).containsExactly("businessRegistrationNumber");
        var verified = verifyBusiness(attempt, "1234567890"); assertThat(verified.statusCode()).as(verified.body()).isEqualTo(200);
        return attempt;
    }
    private String restart() throws Exception {
        var response = post(PATH + "/restart", null, Map.of());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return body(response).path("attemptId").asString();
    }
    private Map<String,Object> payload(String number) {
        return Map.of("profile", Map.of("name", "사업주", "acquisitionChannel", "SEARCH"), "workplace",
                Map.of("workplaceName", "테스트 사업장", "businessRegistrationNumber", number, "businessTypeId", 1,
                        "employeeCountRange", "UNDER_5", "workplaceAddress", "서울시 테스트로 10", "workplaceAddressDetail", "2층",
                        "latitude", 37.5, "longitude", 127.0));
    }
    private HttpResponse<String> verifyBusiness(String attempt, String number) throws Exception {
        return post(PATH + "/business-registration/verify", attempt, Map.of("businessRegistrationNumber", number));
    }
    private HttpResponse<String> ocr(String attempt) throws Exception {
        String boundary = "employer-boundary";
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"businessRegistrationFile\"; filename=\"test.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] magic = new byte[]{(byte)137,80,78,71,13,10,26,10};
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[prefix.length + magic.length + suffix.length];
        System.arraycopy(prefix,0,bytes,0,prefix.length); System.arraycopy(magic,0,bytes,prefix.length,magic.length);
        System.arraycopy(suffix,0,bytes,prefix.length+magic.length,suffix.length);
        return client.send(builder(PATH + "/business-registration/ocr", attempt).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpRequest.Builder builder(String path, String attempt) {
        var builder = HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token);
        if (attempt != null) builder.header("X-Onboarding-Attempt", attempt);
        return builder;
    }
    private HttpRequest jsonRequest(String path, String attempt, Object value) {
        return builder(path, attempt).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(value))).build();
    }
    private HttpResponse<String> post(String path, String attempt, Object value) throws Exception {
        return client.send(jsonRequest(path, attempt, value), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(builder(path, null).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private JsonNode body(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(body(response).path("code").asString()).isEqualTo(code);
    }
}
