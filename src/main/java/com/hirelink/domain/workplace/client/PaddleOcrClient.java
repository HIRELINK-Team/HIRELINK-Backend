package com.hirelink.domain.workplace.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import com.hirelink.domain.workplace.dto.BusinessRegistrationOcrResponse;
import com.hirelink.global.exception.BusinessRegistrationOcrException;
import com.hirelink.global.exception.UnreadableBusinessRegistrationException;

@Component
public class PaddleOcrClient {

	private final String endpoint;
	private final RestClient restClient;

	@Autowired
	public PaddleOcrClient(@Value("${business-registration.paddle.url}") String endpoint) {
		this(endpoint, Duration.ofSeconds(40));
	}

	PaddleOcrClient(String endpoint, Duration readTimeout) {
		this.endpoint = endpoint;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(readTimeout);
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	public BusinessRegistrationOcrResponse recognize(byte[] file, String format) {
		try {
			MediaType contentType = switch (format) {
				case "png" -> MediaType.IMAGE_PNG;
				case "jpg" -> MediaType.IMAGE_JPEG;
				case "pdf" -> MediaType.APPLICATION_PDF;
				default -> throw new BusinessRegistrationOcrException();
			};
			JsonNode response = restClient.post().uri(configuredUri()).contentType(contentType)
					.accept(MediaType.APPLICATION_JSON).body(file).retrieve()
					.onStatus(status -> status.value() != 200, (request, result) -> {
						if (result.getStatusCode().value() == 422) {
							throw new UnreadableBusinessRegistrationException();
						}
						throw new BusinessRegistrationOcrException();
					}).body(JsonNode.class);
			return readResponse(response);
		} catch (RestClientException | IllegalArgumentException exception) {
			throw new BusinessRegistrationOcrException();
		}
	}

	private URI configuredUri() {
		if (!StringUtils.hasText(endpoint)) {
			throw new BusinessRegistrationOcrException();
		}
		URI uri = URI.create(endpoint);
		boolean localHttp = "http".equals(uri.getScheme())
				&& ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "ocr".equals(uri.getHost()));
		if (!localHttp || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
			throw new BusinessRegistrationOcrException();
		}
		return uri;
	}

	private BusinessRegistrationOcrResponse readResponse(JsonNode response) {
		if (response == null || !response.isObject() || response.size() != 2
				|| !response.path("businessRegistrationNumber").isString()
				|| !response.path("businessRegistrationNumber").stringValue().matches("[0-9]{3}-[0-9]{2}-[0-9]{5}")) {
			throw new BusinessRegistrationOcrException();
		}
		JsonNode address = response.path("workplaceAddress");
		if (!address.isNull() && (!address.isString() || address.stringValue().isBlank() || address.stringValue().length() > 500)) {
			throw new BusinessRegistrationOcrException();
		}
		return new BusinessRegistrationOcrResponse(response.path("businessRegistrationNumber").stringValue(),
				address.isNull() ? null : address.stringValue());
	}
}
