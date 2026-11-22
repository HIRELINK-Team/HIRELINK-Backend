package com.hirelink.domain.student.client;

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
import com.hirelink.domain.student.dto.EnrollmentExtraction;
import com.hirelink.global.exception.StudentDocumentException;
import tools.jackson.databind.JsonNode;

@Component
public class EnrollmentOcrClient {
	private final String baseUrl;
	private final RestClient client;

	public EnrollmentOcrClient(@Value("${student-document.paddle-url:http://localhost:8091}") String baseUrl) {
		this.baseUrl = baseUrl;
		var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER).build());
		factory.setReadTimeout(Duration.ofSeconds(40));
		client = RestClient.builder().requestFactory(factory).build();
	}

	public EnrollmentExtraction recognize(byte[] bytes, String contentType) {
		try {
			URI uri = URI.create(baseUrl);
			if (!"http".equals(uri.getScheme()) || !("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "ocr".equals(uri.getHost()))
					|| uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
					|| !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) { throw failed(); }
			JsonNode result = client.post().uri(uri.resolve("/student/enrollment")).contentType(MediaType.parseMediaType(contentType))
					.body(bytes).retrieve().onStatus(status -> status.value() != 200, (request, response) -> {
						if (response.getStatusCode().value() == 422) { throw EnrollmentExtraction.unreadable(); }
						throw failed();
					}).body(JsonNode.class);
			if (result == null || !result.isObject() || result.size() != 4 || !result.path("universityName").isString()
					|| !(result.path("campusName").isNull() || result.path("campusName").isString())) { throw failed(); }
			return new EnrollmentExtraction(result.path("universityName").stringValue(), result.path("campusName").isNull() ? null : result.path("campusName").stringValue(),
					integer(result.path("gradeLevel")), integer(result.path("semesterCount")));
		} catch (RestClientException | IllegalArgumentException exception) { throw failed(); }
	}
	private Integer integer(JsonNode node) {
		if (node.isNull()) { return null; }
		if (!node.isIntegralNumber() || !node.canConvertToInt()) { throw failed(); }
		return node.intValue();
	}
	private StudentDocumentException failed() {
		return new StudentDocumentException(HttpStatus.BAD_GATEWAY, "ENROLLMENT_OCR_FAILED", "재학증명서 인식 서비스를 이용할 수 없습니다.");
	}
}
