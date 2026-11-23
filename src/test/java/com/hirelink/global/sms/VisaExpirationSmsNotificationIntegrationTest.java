package com.hirelink.global.sms;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.hirelink.domain.student.service.VisaExpirationSmsNotificationService;
import com.hirelink.global.exception.SmsDeliveryException;
import com.hirelink.support.PostgreSQLIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class VisaExpirationSmsNotificationIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String STUDENT_MONTH_MESSAGE =
			"[HIRELINK] 비자 만료 1개월 전입니다. 서비스에서 정보를 확인해 주세요.";
	private static final String STUDENT_WEEK_MESSAGE =
			"[HIRELINK] 비자 만료 1주일 전입니다. 서비스에서 정보를 확인해 주세요.";
	private static final String STUDENT_DAY_MESSAGE =
			"[HIRELINK] 오늘 비자가 만료됩니다. 서비스에서 정보를 확인해 주세요.";
	private static final String EMPLOYER_WEEK_MESSAGE =
			"[HIRELINK] 채용한 유학생의 비자 만료 1주일 전입니다. 서비스에서 확인해 주세요.";
	private static final String EMPLOYER_DAY_MESSAGE =
			"[HIRELINK] 채용한 유학생의 비자가 오늘 만료됩니다. 서비스에서 확인해 주세요.";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
	}

	@Autowired private VisaExpirationSmsNotificationService notifications;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private SmsGateway gateway;

	@BeforeEach
	void prepare() {
		jdbc.execute("truncate table user_account, sms_send_history restart identity cascade");
		reset(gateway);
		when(gateway.send(anyString(), anyString())).thenReturn(new SmsGatewayResult("group-1"));
	}

	@Test
	void monthNoticeUsesTheVerifiedStudentAccountAndNeverNotifiesEmployer() {
		LocalDate expiryDate = LocalDate.of(2027, 3, 31);
		long student = createStudent("01011112222", expiryDate);
		long employer = createUser("EMPLOYER", "01033334444");
		createEmploymentMatch(student, employer, 1);

		notifications.sendDueNotifications(LocalDate.of(2027, 2, 28));

		verify(gateway).send("01011112222", STUDENT_MONTH_MESSAGE);
		verify(gateway, never()).send("01033334444", EMPLOYER_WEEK_MESSAGE);
		assertThat(keys()).containsExactly("visa-expiry:%d:%s:MONTH:%d".formatted(student, expiryDate, student));
	}

	@Test
	void weekNoticeDeduplicatesCompletedMatchesAndNotifiesEachRelatedEmployerOnce() {
		LocalDate expiryDate = LocalDate.of(2027, 6, 30);
		long student = createStudent("01011112222", expiryDate);
		long firstEmployer = createUser("EMPLOYER", "01033334444");
		long secondEmployer = createUser("EMPLOYER", "01055556666");
		createUser("EMPLOYER", "01077778888");
		createEmploymentMatch(student, firstEmployer, 1);
		createEmploymentMatch(student, firstEmployer, 2);
		createEmploymentMatch(student, secondEmployer, 3);

		notifications.sendDueNotifications(expiryDate.minusWeeks(1));

		verify(gateway).send("01011112222", STUDENT_WEEK_MESSAGE);
		verify(gateway, times(1)).send("01033334444", EMPLOYER_WEEK_MESSAGE);
		verify(gateway, times(1)).send("01055556666", EMPLOYER_WEEK_MESSAGE);
		verify(gateway, never()).send("01077778888", EMPLOYER_WEEK_MESSAGE);
		assertThat(keys()).containsExactlyInAnyOrder(
				"visa-expiry:%d:%s:WEEK:%d".formatted(student, expiryDate, student),
				"visa-expiry:%d:%s:WEEK:%d".formatted(student, expiryDate, firstEmployer),
				"visa-expiry:%d:%s:WEEK:%d".formatted(student, expiryDate, secondEmployer));
	}

	@Test
	void expiryDayNotifiesStudentWithoutAnyEmploymentAndNonTargetDayDoesNothing() {
		LocalDate expiryDate = LocalDate.of(2027, 6, 30);
		createStudent("01011112222", expiryDate);

		notifications.sendDueNotifications(expiryDate.minusDays(2));
		assertThat(keys()).isEmpty();

		notifications.sendDueNotifications(expiryDate);

		verify(gateway).send("01011112222", STUDENT_DAY_MESSAGE);
		assertThat(keys()).hasSize(1);
	}

	@Test
	void repeatedAndConcurrentRunsCreateOnlyOneHistoryAndOneProviderCall() {
		LocalDate expiryDate = LocalDate.of(2027, 6, 30);
		createStudent("01011112222", expiryDate);

		CompletableFuture<Void> first = CompletableFuture.runAsync(
				() -> notifications.sendDueNotifications(expiryDate));
		CompletableFuture<Void> second = CompletableFuture.runAsync(
				() -> notifications.sendDueNotifications(expiryDate));
		CompletableFuture.allOf(first, second).join();
		notifications.sendDueNotifications(expiryDate);

		assertThat(keys()).hasSize(1);
		verify(gateway, times(1)).send("01011112222", STUDENT_DAY_MESSAGE);
	}

	@Test
	void changedExpiryDateCreatesANewKeyAndPreservesTheOldHistory() {
		LocalDate firstExpiry = LocalDate.of(2027, 6, 30);
		long student = createStudent("01011112222", firstExpiry);
		notifications.sendDueNotifications(firstExpiry.minusWeeks(1));

		LocalDate changedExpiry = LocalDate.of(2027, 8, 31);
		jdbc.update("update student_registration_verification set stay_expires_on = ? where user_id = ?",
				changedExpiry, student);
		jdbc.update("update student_profile set stay_expiration_date = ? where id = ?", changedExpiry, student);
		notifications.sendDueNotifications(changedExpiry.minusWeeks(1));

		assertThat(keys()).containsExactlyInAnyOrder(
				"visa-expiry:%d:%s:WEEK:%d".formatted(student, firstExpiry, student),
				"visa-expiry:%d:%s:WEEK:%d".formatted(student, changedExpiry, student));
	}

	@Test
	void invalidPhoneDoesNotPreventOtherRecipients() {
		LocalDate expiryDate = LocalDate.of(2027, 6, 30);
		createStudent(null, expiryDate);
		createStudent("01033334444", expiryDate);

		notifications.sendDueNotifications(expiryDate);

		assertThat(keys()).hasSize(1);
		verify(gateway).send("01033334444", STUDENT_DAY_MESSAGE);
	}

	@Test
	void providerFailureMarksHistoriesFailedWithoutChangingVisaEmploymentOrPopups() {
		LocalDate expiryDate = LocalDate.of(2027, 6, 30);
		long student = createStudent("01011112222", expiryDate);
		long employer = createUser("EMPLOYER", "01033334444");
		createEmploymentMatch(student, employer, 1);
		when(gateway.send(anyString(), anyString())).thenThrow(new SmsDeliveryException());

		notifications.sendDueNotifications(expiryDate);

		assertThat(jdbc.queryForList("select status from sms_send_history", String.class)).containsOnly("FAILED");
		assertThat(jdbc.queryForObject(
				"select stay_expires_on from student_registration_verification where user_id = ?",
				LocalDate.class, student)).isEqualTo(expiryDate);
		assertThat(jdbc.queryForObject("select count(*) from employment_match", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from employment_popup_notification", Integer.class)).isZero();
		verify(gateway).send("01011112222", STUDENT_DAY_MESSAGE);
		verify(gateway).send("01033334444", EMPLOYER_DAY_MESSAGE);
	}

	@Test
	void allMessagesFitWithinNinetyEucKrBytes() {
		Charset eucKr = Charset.forName("EUC-KR");

		for (String message : List.of(STUDENT_MONTH_MESSAGE, STUDENT_WEEK_MESSAGE, STUDENT_DAY_MESSAGE,
				EMPLOYER_WEEK_MESSAGE, EMPLOYER_DAY_MESSAGE)) {
			assertThat(eucKr.newEncoder().canEncode(message)).as(message).isTrue();
			assertThat(message.getBytes(eucKr).length).as(message).isLessThanOrEqualTo(90);
		}
	}

	private long createStudent(String phone, LocalDate expiryDate) {
		long student = createUser("STUDENT", phone);
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level,
						visa_code, stay_expiration_date)
				values (?, '2000-01-01', 'VN', '서울', 'LEVEL_3', 'D-2', ?)
				""", student, expiryDate);
		jdbc.update("""
				insert into student_registration_verification(user_id, nationality, visa_code, stay_expires_on,
						card_verified_at, registered_at)
				values (?, 'VN', 'D-2', ?, now(), now())
				""", student, expiryDate);
		return student;
	}

	private long createUser(String role, String phone) {
		return jdbc.queryForObject("""
				insert into user_account(name, role, email, phone_number, phone_verified_at)
				values ('테스트', ?, concat(gen_random_uuid(), '@test.com'), ?, now()) returning id
				""", Long.class, role, phone);
	}

	private void createEmploymentMatch(long student, long employer, int sequence) {
		long workplace = jdbc.queryForObject("""
				insert into workplace(employer_user_id, workplace_name, representative_name,
						business_registration_file_key, business_registration_number, business_type_id,
						workplace_address, latitude, longitude, employee_count_range)
				values (?, '사업장', '대표', 'key', ?, 1, '서울', 37.5, 127.0, '1-4') returning id
				""", Long.class, employer, "000-00-%05d".formatted(sequence));
		long posting = jdbc.queryForObject(
				"insert into job_posting(workplace_id, status) values (?, 'CLOSED') returning id",
				Long.class, workplace);
		long batch = jdbc.queryForObject("""
				insert into recommendation_batch(student_profile_id, algorithm_version, input_snapshot, created_at)
				values (?, 'v1', '{}'::jsonb, now()) returning id
				""", Long.class, student);
		long recommendation = jdbc.queryForObject("""
				insert into recommendation(batch_id, job_posting_id, rank, score, created_at)
				values (?, ?, 1, 100, now()) returning id
				""", Long.class, batch, posting);
		long application = jdbc.queryForObject("""
				insert into application(recommendation_id, student_profile_id, job_posting_id,
						desired_start_date, snapshot_version, application_snapshot, status,
						employer_response_deadline, applied_at)
				values (?, ?, ?, current_date, 1, '{}'::jsonb, 'COMPLETED', now(), now()) returning id
				""", Long.class, recommendation, student, posting);
		long offer = jdbc.queryForObject("""
				insert into employment_offer(application_id, status, student_response_deadline, offered_at, responded_at)
				values (?, 'ACCEPTED', now(), now(), now()) returning id
				""", Long.class, application);
		jdbc.update("insert into employment_match(employment_offer_id, completed_at) values (?, now())", offer);
	}

	private List<String> keys() {
		return jdbc.queryForList("select idempotency_key from sms_send_history order by id", String.class);
	}
}
