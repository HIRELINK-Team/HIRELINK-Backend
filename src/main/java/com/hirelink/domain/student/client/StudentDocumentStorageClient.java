package com.hirelink.domain.student.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import com.hirelink.global.exception.StudentDocumentException;
import tools.jackson.databind.JsonNode;

@Component
public class StudentDocumentStorageClient {
	private final String url;
	private final String secret;
	private final String bucket;
	private final RestClient client;

	public StudentDocumentStorageClient(@Value("${storage.supabase.url:}") String url,
			@Value("${storage.supabase.secret-key:}") String secret,
			@Value("${student-document.bucket:student-documents}") String bucket) {
		this.url = url.replaceAll("/+$", ""); this.secret = secret; this.bucket = bucket;
		var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		client = RestClient.builder().requestFactory(factory).build();
	}

	public void upload(String key, byte[] bytes, String contentType) {
		try {
			validate(key);
			JsonNode info = client.get().uri(uri("bucket", bucket)).header("apikey", secret).retrieve().body(JsonNode.class);
			if (info == null || !info.path("public").isBoolean() || info.path("public").booleanValue()) { throw failed(); }
			client.post().uri(uri("object", bucket, key)).header("apikey", secret).header("x-upsert", "false")
					.contentType(MediaType.parseMediaType(contentType)).body(bytes).retrieve().toBodilessEntity();
		} catch (RestClientException | IllegalArgumentException exception) { throw failed(); }
	}

	public void delete(String key) {
		try {
			validate(key);
			client.method(HttpMethod.DELETE).uri(uri("object", bucket)).header("apikey", secret).contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("prefixes", List.of(key))).retrieve().toBodilessEntity();
		} catch (RestClientException | IllegalArgumentException exception) { throw failed(); }
	}

	private URI uri(String... segments) {
		String[] parts = Arrays.stream(segments).flatMap(segment -> Arrays.stream(segment.split("/"))).toArray(String[]::new);
		return UriComponentsBuilder.fromUriString(url).pathSegment("storage", "v1").pathSegment(parts).build().encode().toUri();
	}
	private void validate(String key) {
		URI base = URI.create(url);
		boolean local = "http".equals(base.getScheme()) && ("127.0.0.1".equals(base.getHost()) || "localhost".equals(base.getHost()));
		if ((!"https".equals(base.getScheme()) && !local) || base.getHost() == null || base.getUserInfo() != null
				|| base.getQuery() != null || base.getFragment() != null || !base.getPath().isEmpty() || secret.isBlank()
				|| !bucket.matches("[A-Za-z0-9_-]+") || key == null || !key.matches("(?:enrollment|transcript|topik)/[0-9]+/[0-9a-f-]{36}")) { throw failed(); }
	}
	private StudentDocumentException failed() {
		return new StudentDocumentException(HttpStatus.BAD_GATEWAY, "STUDENT_DOCUMENT_STORAGE_FAILED", "서류 저장소를 이용할 수 없습니다.");
	}
}
