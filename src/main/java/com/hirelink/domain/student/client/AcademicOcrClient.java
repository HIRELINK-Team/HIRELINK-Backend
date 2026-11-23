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
import com.hirelink.domain.student.dto.AcademicExtraction;
import com.hirelink.global.exception.StudentDocumentException;
import tools.jackson.databind.JsonNode;

@Component
public class AcademicOcrClient {
	private final String baseUrl;
	private final RestClient client;

	public AcademicOcrClient(@Value("${student-document.paddle-url:http://localhost:8091}") String baseUrl) {
		this.baseUrl = baseUrl;
		var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER).build());
		factory.setReadTimeout(Duration.ofSeconds(40));
		client = RestClient.builder().requestFactory(factory).build();
	}

	public AcademicExtraction recognize(String type, byte[] bytes, String contentType) {
		try {
			if (!java.util.Set.of("TRANSCRIPT","TOPIK").contains(type)) { throw failed(); }
            URI uri = URI.create(baseUrl);
			if (!"http".equals(uri.getScheme()) || !("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "ocr".equals(uri.getHost()))
					|| uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
					|| !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) { throw failed(); }
			JsonNode result = client.post().uri(uri.resolve("/student/" + type.toLowerCase(java.util.Locale.ROOT))).contentType(MediaType.parseMediaType(contentType))
					.body(bytes).retrieve().onStatus(status -> status.value() != 200, (request, response) -> {
						if (response.getStatusCode().value() == 422) { throw AcademicExtraction.unreadable(); }
						throw failed();
					}).body(JsonNode.class);
            if (result == null || !result.isObject() || result.size()!=4
                    || !(result.path("semester").isNull() || result.path("semester").isString())
                    || !(result.path("averageGpa").isNull() || result.path("averageGpa").isString())) { throw failed(); }
            Integer level=integer(result.path("topikLevel"));
            if(level!=null && (level<1 || level>6)) throw AcademicExtraction.unreadable();
            var value=new AcademicExtraction(integer(result.path("academicYear")), result.path("semester").isNull()?null:result.path("semester").stringValue(),
                result.path("averageGpa").isNull()?null:new java.math.BigDecimal(result.path("averageGpa").stringValue()), level==null?null:level.shortValue());
            value.validate(type);
            return value;
		} catch (RestClientException | IllegalArgumentException exception) { throw failed(); }
	}
	private Integer integer(JsonNode node) {
		if (node.isNull()) { return null; }
		if (!node.isIntegralNumber() || !node.canConvertToInt()) { throw failed(); }
		return node.intValue();
	}
	private StudentDocumentException failed() {
		return new StudentDocumentException(HttpStatus.BAD_GATEWAY, "STUDENT_DOCUMENT_OCR_FAILED", "서류 인식 서비스를 이용할 수 없습니다.");
	}
}
