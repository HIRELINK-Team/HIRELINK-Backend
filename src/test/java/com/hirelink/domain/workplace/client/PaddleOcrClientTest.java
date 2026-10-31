package com.hirelink.domain.workplace.client;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hirelink.domain.workplace.dto.BusinessRegistrationOcrResponse;
import com.hirelink.global.exception.BusinessRegistrationOcrException;
import com.hirelink.global.exception.UnreadableBusinessRegistrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaddleOcrClientTest {

	private HttpServer server;
	private String url;
	private PaddleOcrClient client;
	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicReference<byte[]> body = new AtomicReference<>();
	private final AtomicReference<String> contentType = new AtomicReference<>();
	private volatile int status = 200;
	private volatile long delay;
	private volatile String response = """
			{"businessRegistrationNumber":"123-45-67890","workplaceAddress":"테스트 주소"}
			""";

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/recognize", exchange -> {
			calls.incrementAndGet();
			body.set(exchange.getRequestBody().readAllBytes());
			contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			try {
				Thread.sleep(delay);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.getResponseHeaders().set("Location", url);
				byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(status, bytes.length);
				exchange.getResponseBody().write(bytes);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		server.start();
		url = "http://127.0.0.1:" + server.getAddress().getPort() + "/recognize";
		client = new PaddleOcrClient(url);
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void sendsBinaryFileAndReadsNumberAndOptionalAddress() {
		byte[] file = "synthetic-document".getBytes(StandardCharsets.UTF_8);
		assertThat(client.recognize(file, "png")).isEqualTo(new BusinessRegistrationOcrResponse("123-45-67890", "테스트 주소"));
		assertThat(body.get()).isEqualTo(file);
		assertThat(contentType.get()).isEqualTo("image/png");
		response = "{\"businessRegistrationNumber\":\"123-45-67890\",\"workplaceAddress\":null}";
		assertThat(client.recognize(file, "pdf").workplaceAddress()).isNull();
		assertThat(contentType.get()).isEqualTo("application/pdf");
	}

	@Test
	void rejectsHttpErrorsRedirectsAndMalformedResponsesWithoutDisclosingDocument() {
		for (int code : new int[] {301, 400, 401, 403, 429, 500}) {
			status = code;
			response = "document-content";
			int before = calls.get();
			assertSafeFailure(client);
			assertThat(calls.get()).isEqualTo(before + 1);
		}
		status = 200;
		for (String value : List.of("{", "null", "{}",
				"{\"businessRegistrationNumber\":\"1234567890\",\"workplaceAddress\":null}",
				"{\"businessRegistrationNumber\":\"123-45-67890\",\"workplaceAddress\":false}",
				"{\"businessRegistrationNumber\":\"123-45-67890\"}",
				"{\"businessRegistrationNumber\":\"123-45-67890\",\"workplaceAddress\":null,\"raw\":\"document\"}")) {
			response = value;
			assertSafeFailure(client);
		}
	}

	@Test
	void distinguishesUnreadableDocumentFromServiceFailure() {
		status = 422;
		response = "document-content";
		assertThatThrownBy(() -> client.recognize(new byte[] {1}, "pdf"))
				.isInstanceOf(UnreadableBusinessRegistrationException.class).hasNoCause()
				.hasMessageNotContaining("document-content");
	}

	@Test
	void missingOrExternalConfigurationDoesNotSendDocument() {
		for (String endpoint : List.of("", "invalid url", "http://example.com/recognize", "https://localhost/recognize",
				"http://user@localhost/recognize", url + "?key=test", url + "#fragment")) {
			assertSafeFailure(new PaddleOcrClient(endpoint));
		}
		assertThat(calls.get()).isZero();
	}

	@Test
	void timeoutAndConnectionFailureAreSafeAndAreNotRetried() {
		delay = 250;
		assertSafeFailure(new PaddleOcrClient(url, Duration.ofMillis(50)));
		assertThat(calls.get()).isEqualTo(1);
		server.stop(0);
		assertSafeFailure(client);
	}

	private void assertSafeFailure(PaddleOcrClient candidate) {
		assertThatThrownBy(() -> candidate.recognize(new byte[] {1}, "png"))
				.isInstanceOf(BusinessRegistrationOcrException.class).hasNoCause()
				.hasMessage("사업자등록증 인식 서비스를 이용할 수 없습니다.");
	}
}
