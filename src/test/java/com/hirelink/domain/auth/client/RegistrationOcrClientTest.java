package com.hirelink.domain.auth.client;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.hirelink.global.exception.SocialAuthException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationOcrClientTest {
	@Test
	void usesInternalImageEndpointsAndDoesNotExposeUpstreamErrors() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		AtomicInteger calls = new AtomicInteger();
		server.createContext("/registration/front", exchange -> {
			calls.incrementAndGet();
			assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("image/png");
			assertThat(exchange.getRequestBody().readAllBytes()).containsExactly((byte) 1);
			byte[] body = "{\"name\":\"TEST\"}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.createContext("/registration/back", exchange -> {
			byte[] body = "sensitive upstream data".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(422, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			String url = "http://127.0.0.1:" + server.getAddress().getPort();
			RegistrationOcrClient client = new RegistrationOcrClient(url);
			assertThat(client.recognize(new byte[]{1}, "image/png", true).path("name").asString()).isEqualTo("TEST");
			assertThatThrownBy(() -> client.recognize(new byte[]{1}, "image/png", false))
					.isInstanceOfSatisfying(SocialAuthException.class, e -> assertThat(e.getCode()).isEqualTo("UNREADABLE_REGISTRATION"))
					.hasNoCause().hasMessageNotContaining("sensitive");
			for (String invalid : List.of("", "invalid url", "https://example.com", "http://example.com", url + "?key=secret", url + "/unexpected", url + "#fragment")) {
				assertThatThrownBy(() -> new RegistrationOcrClient(invalid).recognize(new byte[]{1}, "image/png", true))
						.isInstanceOf(SocialAuthException.class).hasNoCause();
			}
			assertThat(calls).hasValue(1);
		} finally { server.stop(0); }
	}
}
