package com.hirelink.global.sms;

import java.util.List;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.hirelink.global.exception.DuplicateSmsException;
import com.hirelink.global.exception.InvalidSmsRequestException;
import com.hirelink.global.exception.SmsDeliveryException;
import com.hirelink.support.PostgreSQLIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class SmsSenderIntegrationTest extends PostgreSQLIntegrationTest {

	private static final String RECIPIENT = "01012345678";
	private static final String MESSAGE = "테스트 문자";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
	}

	@Autowired private SmsSender sender;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TransactionTemplate transactionTemplate;
	@MockitoBean private SmsGateway gateway;

	@BeforeEach
	void prepare() {
		jdbc.execute("truncate table sms_send_history restart identity");
		reset(gateway);
		when(gateway.send(anyString(), anyString())).thenReturn(new SmsGatewayResult("group-1"));
	}

	@Test
	void commitsPendingHistoryBeforeCallingProviderAndMarksSent() {
		sender.send(new SmsSendRequest(RECIPIENT, MESSAGE, "event:1"));

		assertThat(jdbc.queryForObject("select status from sms_send_history", String.class)).isEqualTo("SENT");
		assertThat(jdbc.queryForObject("select provider_message_id from sms_send_history", String.class))
				.isEqualTo("group-1");
		assertThat(jdbc.queryForObject("select sent_at is not null from sms_send_history", Boolean.class)).isTrue();
		verify(gateway).send(RECIPIENT, MESSAGE);
	}

	@Test
	void savesFailedStatusWithoutRetryWhenProviderRejects() {
		when(gateway.send(anyString(), anyString())).thenThrow(new SmsDeliveryException());

		sender.send(new SmsSendRequest(RECIPIENT, MESSAGE, "event:failed"));

		assertThat(jdbc.queryForObject("select status from sms_send_history", String.class)).isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("select failed_at is not null from sms_send_history", Boolean.class)).isTrue();
		verify(gateway).send(RECIPIENT, MESSAGE);
	}

	@Test
	void rollbackRemovesPendingHistoryAndNeverCallsProvider() {
		transactionTemplate.executeWithoutResult(status -> {
			sender.send(new SmsSendRequest(RECIPIENT, MESSAGE, "event:rollback"));
			status.setRollbackOnly();
		});

		assertThat(jdbc.queryForObject("select count(*) from sms_send_history", Integer.class)).isZero();
		verify(gateway, never()).send(anyString(), anyString());
	}

	@Test
	void uniqueConstraintAllowsOnlyOneConcurrentRequestForSameKey() throws Exception {
		CompletableFuture<Throwable> first = sendConcurrently("event:concurrent");
		CompletableFuture<Throwable> second = sendConcurrently("event:concurrent");

		List<Throwable> failures = Arrays.asList(first.get(), second.get()).stream()
				.filter(error -> error != null).toList();
		assertThat(failures).hasSize(1);
		assertThat(rootCause(failures.get(0))).isInstanceOf(DuplicateSmsException.class);
		assertThat(jdbc.queryForObject("select count(*) from sms_send_history", Integer.class)).isEqualTo(1);
		verify(gateway, times(1)).send(RECIPIENT, MESSAGE);
	}

	@Test
	void rejectsInvalidRecipientMessageAndIdempotencyKeyBeforePersistence() {
		for (SmsSendRequest request : List.of(
				new SmsSendRequest("010-1234-5678", MESSAGE, "valid-key"),
				new SmsSendRequest(RECIPIENT, " ", "valid-key"),
				new SmsSendRequest(RECIPIENT, MESSAGE, "invalid key"))) {
			assertThatThrownBy(() -> sender.send(request)).isInstanceOf(InvalidSmsRequestException.class);
		}
		assertThat(jdbc.queryForObject("select count(*) from sms_send_history", Integer.class)).isZero();
		verify(gateway, never()).send(anyString(), anyString());
	}

	private CompletableFuture<Throwable> sendConcurrently(String key) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				sender.send(new SmsSendRequest(RECIPIENT, MESSAGE, key));
				return null;
			} catch (Throwable exception) {
				return exception;
			}
		});
	}

	private Throwable rootCause(Throwable exception) {
		Throwable current = exception;
		while ((current instanceof ExecutionException || current instanceof java.util.concurrent.CompletionException)
				&& current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}
}
