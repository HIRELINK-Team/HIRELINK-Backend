package com.hirelink.domain.workplace.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hirelink.global.exception.BusinessRegistrationLookupException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NtsBusinessRegistrationClientTest {

	private static final String NUMBER = "1234567890";
	private HttpServer server;
	private URI statusUri;
	private NtsBusinessRegistrationClient client;
	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicReference<String> method = new AtomicReference<>();
	private final AtomicReference<String> query = new AtomicReference<>();
	private final AtomicReference<String> body = new AtomicReference<>();
	private volatile int responseStatus = 200;
	private volatile String responseBody = statusResponse("01", "부가가치세 일반과세자");
	private volatile long responseDelayMillis;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/status", exchange -> {
			calls.incrementAndGet();
			method.set(exchange.getRequestMethod());
			query.set(exchange.getRequestURI().getRawQuery());
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			try {
				Thread.sleep(responseDelayMillis);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				if (responseStatus == 302) {
					exchange.getResponseHeaders().set("Location", statusUri.toString());
				}
				byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(responseStatus, response.length);
				exchange.getResponseBody().write(response);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		server.start();
		statusUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/status");
		client = new NtsBusinessRegistrationClient("test+key/value=", statusUri);
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void sendsNumberInJsonAndEncodesKeyOnceThenAcceptsActiveBusiness() {
		assertThat(client.isActiveBusiness(NUMBER)).isTrue();
		assertThat(method.get()).isEqualTo("POST");
		assertThat(body.get()).isEqualTo("{\"b_no\":[\"1234567890\"]}");
		assertThat(query.get()).isEqualTo("serviceKey=test%2Bkey%2Fvalue%3D&returnType=JSON");
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void suspendedClosedAndUnregisteredBusinessesAreNotActive() {
		for (String status : new String[] {"02", "03"}) {
			responseBody = statusResponse(status, "부가가치세 일반과세자");
			assertThat(client.isActiveBusiness(NUMBER)).isFalse();
		}
		responseBody = statusResponse("", "국세청에 등록되지 않은 사업자등록번호입니다");
		assertThat(client.isActiveBusiness(NUMBER)).isFalse();
	}

	@Test
	void malformedOrUnrecognizedResponsesAreFailuresRatherThanInvalidBusinesses() {
		for (String response : new String[] {"{", "null", "{}", "{\"status_code\":\"ERROR\",\"data\":[]}",
				"{\"status_code\":1,\"data\":[]}",
				"{\"status_code\":\"OK\",\"data\":[]}", "{\"status_code\":\"OK\",\"data\":[null]}",
				statusResponse("01", "").replace(NUMBER, "9999999999"), statusResponse("99", ""),
				statusResponse("", ""), statusResponse("01", "").replace("\"b_stt_cd\":\"01\"", "\"b_stt_cd\":1"),
				statusResponse("01", "").replace("\"b_no\":\"1234567890\"", "\"b_no\":1234567890"),
				statusResponse("", "").replace("\"tax_type\":\"\"", "\"tax_type\":null"),
				"{\"status_code\":\"OK\",\"data\":[{\"b_no\":\"1234567890\"}]}",
				"{\"status_code\":\"OK\",\"data\":[{},{}]}"}) {
			responseBody = response;
			assertLookupFailure(client);
		}
	}

	@Test
	void httpErrorsAndRedirectsDoNotExposeRemoteDetailsOrForwardKey() {
		for (int status : new int[] {302, 400, 401, 429, 500}) {
			responseStatus = status;
			responseBody = "{\"message\":\"test+key/value= remote-details\"}";
			int before = calls.get();
			assertLookupFailure(client);
			assertThat(calls.get()).isEqualTo(before + 1);
		}
	}

	@Test
	void missingKeyFailsWithoutCallingExternalService() {
		assertLookupFailure(new NtsBusinessRegistrationClient("", statusUri));
		assertLookupFailure(new NtsBusinessRegistrationClient("   ", statusUri));
		assertThat(calls.get()).isZero();
	}

	@Test
	void connectionFailureReturnsSafeLookupError() {
		server.stop(0);
		assertLookupFailure(client);
	}

	@Test
	void slowResponseTimesOutWithSafeLookupError() {
		responseDelayMillis = 6000;
		assertLookupFailure(client);
	}

	private void assertLookupFailure(NtsBusinessRegistrationClient target) {
		assertThatThrownBy(() -> target.isActiveBusiness(NUMBER))
				.isInstanceOf(BusinessRegistrationLookupException.class)
				.hasMessage("사업자등록 상태를 조회하지 못했습니다. 잠시 후 다시 시도해주세요.")
				.hasNoCause();
	}

	private static String statusResponse(String code, String taxType) {
		return """
				{"status_code":"OK","request_cnt":1,"match_cnt":%d,"data":[
				{"b_no":"1234567890","b_stt_cd":"%s","tax_type":"%s","end_dt":""}]}
				""".formatted(code.isEmpty() ? 0 : 1, code, taxType);
	}
}
