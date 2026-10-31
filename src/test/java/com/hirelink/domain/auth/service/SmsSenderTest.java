package com.hirelink.domain.auth.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.hirelink.global.exception.SmsDeliveryException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsSenderTest {

	private static final String API_KEY = "test-api-key";
	private static final String API_SECRET = "test-api-secret";
	private static final String SENDER = "01000000000";
	private static final String RECIPIENT = "01000000001";
	private static final String CODE = "000123";
	private static final String ACCEPTED = """
			{"groupInfo":{"count":{"total":1,"registeredSuccess":1,"registeredFailed":0}},
			"failedMessageList":[]}
			""";
	private HttpServer server;
	private URI sendUri;
	private SmsSender sender;
	private final ObjectMapper mapper = new ObjectMapper();
	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicReference<String> method = new AtomicReference<>();
	private final AtomicReference<String> authorization = new AtomicReference<>();
	private final AtomicReference<String> requestBody = new AtomicReference<>();
	private volatile int responseStatus = 200;
	private volatile String responseBody = ACCEPTED;
	private volatile long responseDelayMillis;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/send", exchange -> {
			calls.incrementAndGet();
			method.set(exchange.getRequestMethod());
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			try {
				Thread.sleep(responseDelayMillis);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				if (responseStatus == 302) {
					exchange.getResponseHeaders().set("Location", sendUri.toString());
				}
				byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(responseStatus, bytes.length);
				exchange.getResponseBody().write(bytes);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		server.start();
		sendUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/send");
		sender = new SmsSender(API_KEY, API_SECRET, SENDER, sendUri);
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void sendsSingleSmsWithSignedAuthorizationAndPreservesLeadingZero() throws Exception {
		sender.sendVerificationCode(RECIPIENT, CODE);
		assertThat(method.get()).isEqualTo("POST");
		JsonNode body = mapper.readTree(requestBody.get());
		assertThat(body.path("messages").size()).isEqualTo(1);
		JsonNode message = body.path("messages").get(0);
		assertThat(message.path("to").stringValue()).isEqualTo(RECIPIENT);
		assertThat(message.path("from").stringValue()).isEqualTo(SENDER);
		assertThat(message.path("type").stringValue()).isEqualTo("SMS");
		String text = message.path("text").stringValue();
		assertThat(text).isEqualTo("[HIRELINK] 인증번호 [000123]");
		assertThat(text.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(90);
		assertThat(requestBody.get()).doesNotContain(API_KEY, API_SECRET);
		assertThat(authorization.get()).startsWith("HMAC-SHA256 ").doesNotContain(API_SECRET);
		Map<String, String> auth = Stream.of(authorization.get().substring("HMAC-SHA256 ".length()).split(", "))
				.map(part -> part.split("=", 2)).collect(Collectors.toMap(part -> part[0], part -> part[1]));
		assertThat(auth.get("apiKey")).isEqualTo(API_KEY);
		assertThat(auth.get("salt")).matches("[0-9a-f]{32}");
		assertThat(Duration.between(Instant.parse(auth.get("date")), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(API_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		assertThat(auth.get("signature")).isEqualTo(HexFormat.of().formatHex(
				mac.doFinal((auth.get("date") + auth.get("salt")).getBytes(StandardCharsets.UTF_8))));
		String firstAuthorization = authorization.get();
		sender.sendVerificationCode(RECIPIENT, CODE);
		assertThat(authorization.get()).isNotEqualTo(firstAuthorization);
		assertThat(calls.get()).isEqualTo(2);
	}

	@Test
	void missingConfigurationFailsWithoutExternalCall() {
		for (String missing : new String[] {null, "", "   "}) {
			assertDeliveryFailure(new SmsSender(missing, API_SECRET, SENDER, sendUri));
			assertDeliveryFailure(new SmsSender(API_KEY, missing, SENDER, sendUri));
			assertDeliveryFailure(new SmsSender(API_KEY, API_SECRET, missing, sendUri));
		}
		assertThat(calls.get()).isZero();
	}

	@Test
	void providerRejectionInSuccessfulHttpResponseIsStillFailure() {
		responseBody = """
				{"groupInfo":{"count":{"total":1,"registeredSuccess":0,"registeredFailed":1}},
				"failedMessageList":[{"statusCode":"3040","statusMessage":"remote-details"}]}
				""";
		assertDeliveryFailure(sender);
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void malformedOrInconsistentResponseIsNotAccepted() {
		for (String body : new String[] {"{", "null", "", "{}",
				ACCEPTED.replace("\"registeredSuccess\":1", "\"registeredSuccess\":\"1\""),
				ACCEPTED.replace("\"registeredSuccess\":1", "\"registeredSuccess\":1.5"),
				ACCEPTED.replace("\"total\":1", "\"total\":2"),
				ACCEPTED.replace("\"registeredFailed\":0", "\"registeredFailed\":1"),
				ACCEPTED.replace("[]", "[{}]"), ACCEPTED.replace("[]", "null")}) {
			responseBody = body;
			assertDeliveryFailure(sender);
		}
	}

	@Test
	void httpErrorsAndRedirectsAreNotRetriedAndDoNotExposeProviderDetails() {
		for (int status : new int[] {302, 401, 403, 429, 500}) {
			responseStatus = status;
			responseBody = "{\"message\":\"" + API_SECRET + " " + RECIPIENT + " " + CODE + "\"}";
			int before = calls.get();
			assertDeliveryFailure(sender);
			assertThat(calls.get()).isEqualTo(before + 1);
		}
	}

	@Test
	void connectionFailureReturnsDeliveryErrorWithoutDetails() {
		server.stop(0);
		assertDeliveryFailure(sender);
	}

	@Test
	void slowResponseTimesOutWithoutRetry() {
		responseDelayMillis = 6000;
		assertDeliveryFailure(sender);
		assertThat(calls.get()).isEqualTo(1);
	}

	private void assertDeliveryFailure(SmsSender target) {
		assertThatThrownBy(() -> target.sendVerificationCode(RECIPIENT, CODE))
				.isInstanceOf(SmsDeliveryException.class).hasMessage("인증번호를 전송하지 못했습니다.").hasNoCause();
	}
}
