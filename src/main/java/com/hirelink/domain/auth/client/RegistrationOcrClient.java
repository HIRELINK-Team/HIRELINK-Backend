package com.hirelink.domain.auth.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hirelink.global.exception.SocialAuthException;
import tools.jackson.databind.JsonNode;

@Component
public class RegistrationOcrClient {

	private final String baseUrl;
	private final RestClient client;

	public RegistrationOcrClient(@Value("${registration-verification.paddle-url:}") String baseUrl) {
		this.baseUrl = baseUrl;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
		factory.setReadTimeout(Duration.ofSeconds(40));
		client = RestClient.builder().requestFactory(factory).build();
	}

	public JsonNode recognize(byte[] bytes, String contentType, boolean front) {
		return request(bytes, contentType, front ? "/registration/front" : "/registration/back", false);
	}

	public JsonNode recognizeStayDocument(byte[] bytes) {
		return request(bytes, "application/pdf", "/registration/stay-document", true);
	}

	private JsonNode request(byte[] bytes, String contentType, String path, boolean document) {
		try {
			URI uri = URI.create(baseUrl);
			if (!"http".equals(uri.getScheme()) || !("localhost".equals(uri.getHost())
					|| "127.0.0.1".equals(uri.getHost()) || "ocr".equals(uri.getHost()))
					|| uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
					|| !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
				throw failed();
			}
			JsonNode response = client.post().uri(uri.resolve(path))
					.contentType(MediaType.parseMediaType(contentType)).accept(MediaType.APPLICATION_JSON).body(bytes)
					.retrieve().onStatus(status -> status.value() != 200, (request, result) -> {
						if (result.getStatusCode().value() == 422) { throw document ? unreadableDocument() : unreadable(); }
						throw failed();
					}).body(JsonNode.class);
			if (response == null || !response.isObject()) { throw failed(); }
			return response;
		} catch (RestClientException | IllegalArgumentException exception) {
			throw failed();
		}
	}

	public static SocialAuthException unreadable() {
		return new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_REGISTRATION", "등록증 정보를 확인하지 못했습니다. 선명하게 다시 촬영해주세요.");
	}

	public static SocialAuthException unreadableDocument() {
		return new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_STAY_DOCUMENT", "체류기간 증빙을 읽을 수 없습니다. 외국인등록 사실증명 PDF를 다시 선택해주세요.");
	}

	private SocialAuthException failed() {
		return new SocialAuthException(HttpStatus.BAD_GATEWAY, "REGISTRATION_OCR_FAILED", "등록증 인식 서비스를 이용할 수 없습니다.");
	}
}
