package com.hirelink.global.sms;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.employmentnotification.EmploymentSmsNotificationPublisher;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.workplace.entity.Workplace;
import com.hirelink.global.exception.SmsDeliveryException;
import com.hirelink.support.PostgreSQLIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class EmploymentSmsNotificationIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String EMPLOYER_PHONE = "01011112222";
	private static final String STUDENT_PHONE = "01033334444";
	private static final String APPLICATION_CREATED_MESSAGE =
			"[HIRELINK] 새로운 지원자가 있습니다. 서비스에서 확인해 주세요.";
	private static final String APPLICATION_REJECTED_MESSAGE =
			"[HIRELINK] 지원하신 일자리의 채용 결과를 서비스에서 확인해 주세요.";
	private static final String OFFER_CREATED_MESSAGE =
			"[HIRELINK] 새로운 채용 제안이 도착했습니다. 서비스에서 응답해 주세요.";
	private static final String OFFER_ACCEPTED_MESSAGE =
			"[HIRELINK] 채용 제안이 수락되었습니다. 서비스에서 확인해 주세요.";
	private static final String OFFER_REJECTED_MESSAGE =
			"[HIRELINK] 채용 제안이 거절되었습니다. 서비스에서 확인해 주세요.";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
	}

	@Autowired private EmploymentSmsNotificationPublisher notifications;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TransactionTemplate transactionTemplate;
	@MockitoBean private SmsGateway gateway;

	@BeforeEach
	void prepare() {
		jdbc.execute("drop table if exists sms_notification_test_marker");
		jdbc.execute("create table sms_notification_test_marker(id bigint primary key)");
		jdbc.execute("truncate table sms_send_history restart identity");
		reset(gateway);
		when(gateway.send(anyString(), anyString())).thenReturn(new SmsGatewayResult("group-1"));
	}

	@AfterEach
	void cleanUp() {
		jdbc.execute("drop table if exists sms_notification_test_marker");
	}

	@Test
	void sendsEachEventToTheExactAccountWithExactMessageAndStableKey() {
		Application application = application(101L, EMPLOYER_PHONE, STUDENT_PHONE);
		EmploymentOffer offer = offer(202L, application);

		transactionTemplate.executeWithoutResult(status -> notifications.applicationCreated(application));
		transactionTemplate.executeWithoutResult(status -> notifications.applicationRejected(application));
		transactionTemplate.executeWithoutResult(status -> notifications.offerCreated(offer));
		transactionTemplate.executeWithoutResult(status -> notifications.offerAccepted(offer));
		transactionTemplate.executeWithoutResult(status -> notifications.offerRejected(offer));

		verify(gateway).send(EMPLOYER_PHONE, APPLICATION_CREATED_MESSAGE);
		verify(gateway).send(STUDENT_PHONE, APPLICATION_REJECTED_MESSAGE);
		verify(gateway).send(STUDENT_PHONE, OFFER_CREATED_MESSAGE);
		verify(gateway).send(EMPLOYER_PHONE, OFFER_ACCEPTED_MESSAGE);
		verify(gateway).send(EMPLOYER_PHONE, OFFER_REJECTED_MESSAGE);
		assertThat(jdbc.queryForList(
				"select idempotency_key from sms_send_history order by id", String.class)).containsExactly(
				"employment-application-created:101",
				"employment-application-rejected:101",
				"employment-offer-created:202",
				"employment-offer-accepted:202",
				"employment-offer-rejected:202");
		assertThat(jdbc.queryForList("select status from sms_send_history", String.class))
				.containsOnly("SENT");
	}

	@Test
	void allMessagesAreRepresentableAndAtMostNinetyBytesInEucKr() {
		Charset eucKr = Charset.forName("EUC-KR");

		for (String message : List.of(APPLICATION_CREATED_MESSAGE, APPLICATION_REJECTED_MESSAGE,
				OFFER_CREATED_MESSAGE, OFFER_ACCEPTED_MESSAGE, OFFER_REJECTED_MESSAGE)) {
			assertThat(eucKr.newEncoder().canEncode(message)).as(message).isTrue();
			assertThat(message.getBytes(eucKr).length).as(message).isLessThanOrEqualTo(90);
		}
	}

	@Test
	void rollbackDiscardsNotificationBeforeHistoryAndProviderCall() {
		Application application = application(101L, EMPLOYER_PHONE, STUDENT_PHONE);

		transactionTemplate.executeWithoutResult(status -> {
			notifications.applicationCreated(application);
			status.setRollbackOnly();
		});

		assertThat(jdbc.queryForObject("select count(*) from sms_send_history", Integer.class)).isZero();
		verify(gateway, never()).send(anyString(), anyString());
	}

	@Test
	void duplicateNotificationIsIgnoredAfterBusinessCommit() {
		Application application = application(101L, EMPLOYER_PHONE, STUDENT_PHONE);

		transactionTemplate.executeWithoutResult(status -> notifications.applicationCreated(application));
		transactionTemplate.executeWithoutResult(status -> notifications.applicationCreated(application));

		assertThat(jdbc.queryForObject("select count(*) from sms_send_history", Integer.class)).isEqualTo(1);
		verify(gateway, times(1)).send(EMPLOYER_PHONE, APPLICATION_CREATED_MESSAGE);
	}

	@Test
	void invalidRecipientDoesNotChangeCommittedBusinessResultOrCallProvider() {
		Application application = application(101L, null, STUDENT_PHONE);
		int updated = transactionTemplate.execute(status -> {
			notifications.applicationCreated(application);
			return jdbc.update("insert into sms_notification_test_marker(id) values (1)");
		});

		assertThat(updated).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from sms_notification_test_marker", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from sms_send_history", Integer.class)).isZero();
		verify(gateway, never()).send(anyString(), anyString());
	}

	@Test
	void providerFailureKeepsCommittedResultAndMarksOnlyHistoryFailed() {
		when(gateway.send(anyString(), anyString())).thenThrow(new SmsDeliveryException());
		Application application = application(101L, EMPLOYER_PHONE, STUDENT_PHONE);

		int updated = transactionTemplate.execute(status -> {
			notifications.applicationCreated(application);
			return jdbc.update("insert into sms_notification_test_marker(id) values (1)");
		});

		assertThat(updated).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from sms_notification_test_marker", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from sms_send_history", String.class)).isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("select failed_at is not null from sms_send_history", Boolean.class)).isTrue();
	}

	private Application application(Long id, String employerPhone, String studentPhone) {
		UserAccount employer = mock(UserAccount.class);
		when(employer.getPhoneNumber()).thenReturn(employerPhone);
		Workplace workplace = mock(Workplace.class);
		when(workplace.getEmployerUser()).thenReturn(employer);
		JobPosting posting = mock(JobPosting.class);
		when(posting.getWorkplace()).thenReturn(workplace);

		UserAccount student = mock(UserAccount.class);
		when(student.getPhoneNumber()).thenReturn(studentPhone);
		StudentProfile profile = mock(StudentProfile.class);
		when(profile.getUserAccount()).thenReturn(student);

		Application application = mock(Application.class);
		when(application.getId()).thenReturn(id);
		when(application.getJobPosting()).thenReturn(posting);
		when(application.getStudentProfile()).thenReturn(profile);
		return application;
	}

	private EmploymentOffer offer(Long id, Application application) {
		EmploymentOffer offer = mock(EmploymentOffer.class);
		when(offer.getId()).thenReturn(id);
		when(offer.getApplication()).thenReturn(application);
		return offer;
	}
}
