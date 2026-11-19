package com.hirelink.domain.auth.client;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hirelink.global.exception.SocialAuthException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApickRegistrationClientTest {
	private HttpServer server;
	private String url;
	private volatile String response;
	private volatile int status;
	private String requestBody;
	private String contentType;
	private String authorization;
	private final AtomicInteger calls = new AtomicInteger();

	@BeforeEach
	void start() throws Exception {
		status = 200;
		response = "{\"data\":{\"result\":1,\"success\":1},\"api\":{\"success\":true,\"cost\":40}}";
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/verify", exchange -> {
			calls.incrementAndGet();
			requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			contentType = exchange.getRequestHeaders().getFirst("Content-Type");
			authorization = exchange.getRequestHeaders().getFirst("Authorization");
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		url = "http://127.0.0.1:" + server.getAddress().getPort() + "/verify";
	}

	@AfterEach void stop() { server.stop(0); }
	private ApickRegistrationClient client() { return new ApickRegistrationClient("test-key", url, Duration.ofSeconds(2)); }
	private void verify() { client().verify("0001015000000", LocalDate.of(2020, 1, 1), "00000000001"); }

	@Test
	void sendsOnlyRequiredFormFieldsAndSeparatesVerdictFromBilling() {
		verify();
		assertThat(contentType).startsWith("multipart/form-data;");
		assertThat(authorization).isEqualTo("Bearer test-key");
		assertThat(requestBody).contains("name=\"rrn\"", "0001015000000", "name=\"made_date\"", "2020-01-01", "name=\"card_sn\"");
		client().verify("0001015000000", LocalDate.of(2009, 1, 1), null);
		assertThat(requestBody).doesNotContain("card_sn");
		response = "{\"data\":{\"result\":0,\"success\":1,\"msg\":\"sensitive\"},\"api\":{\"success\":true,\"cost\":40}}";
		assertThatThrownBy(this::verify).isInstanceOfSatisfying(SocialAuthException.class,
				e -> assertThat(e.getCode()).isEqualTo("REGISTRATION_NOT_VERIFIED")).hasMessageNotContaining("sensitive");
	}

	@Test
	void rejectsErrorsIndeterminateResultsMalformedResponsesAndOverflow() {
		for (String body : List.of("{}", "not-json",
				"{\"result\":{\"error\":\"sensitive\"},\"api\":{\"success\":true}}",
				"{\"data\":{\"error\":\"sensitive\",\"success\":0},\"api\":{\"success\":true}}",
				"{\"data\":{\"result\":1,\"success\":1},\"api\":{\"success\":false}}",
				"{\"data\":{\"result\":2,\"success\":1},\"api\":{\"success\":true}}",
				"{\"data\":{\"result\":3,\"success\":1},\"api\":{\"success\":true}}",
				"{\"data\":{\"result\":\"1\",\"success\":1},\"api\":{\"success\":true}}",
				"{\"data\":{\"result\":4294967297,\"success\":1},\"api\":{\"success\":true}}")) {
			response = body;
			assertThatThrownBy(this::verify).isInstanceOfSatisfying(SocialAuthException.class,
					e -> assertThat(e.getCode()).isEqualTo("REGISTRATION_VERIFICATION_FAILED")).hasNoCause().hasMessageNotContaining("sensitive");
		}
		for (int code : new int[]{301, 400, 401, 408, 424, 500}) {
			status = code;
			assertThatThrownBy(this::verify).isInstanceOf(SocialAuthException.class).hasNoCause();
		}
	}

	@Test
	void missingKeyDoesNotCallProvider() {
		assertThatThrownBy(() -> new ApickRegistrationClient("", url, Duration.ofSeconds(2))
				.verify("0001015000000", LocalDate.of(2020, 1, 1), "00000000001")).isInstanceOf(SocialAuthException.class);
		assertThat(calls).hasValue(0);
	}
}
