package com.hirelink.global.storage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hirelink.global.exception.ProfileImageAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseStorageClientTest {

	private HttpServer server;
	private String projectUrl;
	private SupabaseStorageClient client;
	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicReference<String> requestPath = new AtomicReference<>();
	private final AtomicReference<String> requestBody = new AtomicReference<>();
	private final AtomicReference<String> apiKey = new AtomicReference<>();
	private final AtomicReference<String> authorization = new AtomicReference<>();
	private volatile int responseStatus = 200;
	private volatile String responseBody = "{\"signedURL\":\"/object/sign/profile-images/profiles/1/photo.png?token=test-token\"}";
	private volatile String redirectLocation;

	@BeforeEach
	void startStorageServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			calls.incrementAndGet();
			requestPath.set(exchange.getRequestURI().getRawPath());
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			if (redirectLocation != null) {
				exchange.getResponseHeaders().set("Location", redirectLocation);
			}
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(responseStatus, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		projectUrl = "http://127.0.0.1:" + server.getAddress().getPort();
		client = new SupabaseStorageClient(projectUrl, "test-storage-key", "profile-images");
	}

	@AfterEach
	void stopStorageServer() {
		server.stop(0);
	}

	@Test
	void requestsFifteenMinuteUrlUsingServerApiKey() {
		String result = client.createSignedUrl("profiles/1/photo.png");
		assertThat(result).isEqualTo(projectUrl + "/storage/v1/object/sign/profile-images/profiles/1/photo.png?token=test-token");
		assertThat(requestPath.get()).isEqualTo("/storage/v1/object/sign/profile-images/profiles/1/photo.png");
		assertThat(requestBody.get()).isEqualTo("{\"expiresIn\":900}");
		assertThat(apiKey.get()).isEqualTo("test-storage-key");
		assertThat(authorization.get()).isNull();
	}

	@Test
	void encodesFileNamesInRequestAndReturnedUrl() {
		responseBody = "{\"signedURL\":\"/object/sign/profile-images/profiles/1/사진 1.png?token=test-token\"}";
		String result = client.createSignedUrl("profiles/1/사진 1.png");
		String encodedName = "%EC%82%AC%EC%A7%84%201.png";
		assertThat(requestPath.get()).isEqualTo("/storage/v1/object/sign/profile-images/profiles/1/" + encodedName);
		assertThat(result).isEqualTo(projectUrl + "/storage/v1/object/sign/profile-images/profiles/1/" + encodedName + "?token=test-token");
	}

	@ParameterizedTest
	@ValueSource(ints = {403, 404, 500})
	void storageErrorsDoNotExposeRemoteBody(int status) {
		responseStatus = status;
		responseBody = "{\"message\":\"remote-detail-with-test-storage-key\"}";
		assertThatThrownBy(() -> client.createSignedUrl("profiles/1/photo.png"))
				.isInstanceOf(ProfileImageAccessException.class)
				.hasMessage("프로필 이미지 주소를 생성하지 못했습니다.")
				.hasNoCause();
	}

	@ParameterizedTest
	@ValueSource(strings = {"{", "{}", "{\"signedURL\":\"https://other.example.com/image?token=test\"}",
			"{\"signedURL\":\"/object/sign/profile-images/photo.png\"}"})
	void rejectsInvalidSigningResponses(String body) {
		responseBody = body;
		assertThatThrownBy(() -> client.createSignedUrl("profiles/1/photo.png"))
				.isInstanceOf(ProfileImageAccessException.class).hasNoCause();
	}

	@Test
	void missingConfigurationAndInsecureUrlFailBeforeSending() {
		SupabaseStorageClient missingKey = new SupabaseStorageClient(projectUrl, "", "profile-images");
		SupabaseStorageClient missingUrl = new SupabaseStorageClient("", "test-storage-key", "profile-images");
		SupabaseStorageClient insecureUrl = new SupabaseStorageClient("http://storage.example.com", "test-storage-key", "profile-images");
		assertThatThrownBy(() -> missingKey.createSignedUrl("photo.png")).isInstanceOf(ProfileImageAccessException.class);
		assertThatThrownBy(() -> missingUrl.createSignedUrl("photo.png")).isInstanceOf(ProfileImageAccessException.class);
		assertThatThrownBy(() -> insecureUrl.createSignedUrl("photo.png")).isInstanceOf(ProfileImageAccessException.class);
		assertThat(calls.get()).isZero();
	}

	@Test
	void rejectsTraversalAndDoesNotFollowRedirects() {
		assertThatThrownBy(() -> client.createSignedUrl("../other-bucket/photo.png")).isInstanceOf(ProfileImageAccessException.class);
		assertThat(calls.get()).isZero();
		responseStatus = 302;
		responseBody = "{}";
		redirectLocation = projectUrl + "/unexpected";
		assertThatThrownBy(() -> client.createSignedUrl("photo.png")).isInstanceOf(ProfileImageAccessException.class);
		assertThat(calls.get()).isEqualTo(1);
		assertThat(requestPath.get()).isEqualTo("/storage/v1/object/sign/profile-images/photo.png");
	}
}
