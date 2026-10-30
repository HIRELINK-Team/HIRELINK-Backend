package com.hirelink.global.storage;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.hirelink.global.exception.ProfileImageAccessException;

@Component
public class SupabaseStorageClient {

	private static final int SIGNED_URL_EXPIRATION_SECONDS = 900;

	private final RestClient restClient;
	private final String projectUrl;
	private final String secretKey;
	private final String bucket;

	public SupabaseStorageClient(@Value("${storage.supabase.url}") String projectUrl,
			@Value("${storage.supabase.secret-key}") String secretKey,
			@Value("${storage.supabase.bucket}") String bucket) {
		this.projectUrl = projectUrl.replaceAll("/+$", "");
		this.secretKey = secretKey;
		this.bucket = bucket;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	public String createSignedUrl(String objectKey) {
		try {
			validateConfiguration();
			if (!StringUtils.hasText(objectKey) || objectKey.contains("\\")
					|| Arrays.stream(objectKey.split("/", -1)).anyMatch(segment -> segment.isBlank()
							|| segment.equals(".") || segment.equals(".."))) {
				throw new ProfileImageAccessException();
			}
			URI uri = UriComponentsBuilder.fromUriString(projectUrl)
					.pathSegment("storage", "v1", "object", "sign", bucket)
					.pathSegment(objectKey.split("/"))
					.build().encode().toUri();
			SignedUrlResponse response = restClient.post().uri(uri)
					.header("apikey", secretKey).contentType(MediaType.APPLICATION_JSON)
					.body(new SignedUrlRequest(SIGNED_URL_EXPIRATION_SECONDS))
					.retrieve().body(SignedUrlResponse.class);
			if (response == null || response.signedURL() == null || !response.signedURL().startsWith("/object/sign/")) {
				throw new ProfileImageAccessException();
			}
			var signedUrl = UriComponentsBuilder.fromUriString(projectUrl + "/storage/v1" + response.signedURL())
					.build();
			if (!StringUtils.hasText(signedUrl.getQueryParams().getFirst("token")) || signedUrl.getFragment() != null) {
				throw new ProfileImageAccessException();
			}
			return signedUrl.encode().toUriString();
		} catch (RestClientException | IllegalArgumentException exception) {
			// 외부 오류 본문의 서명 URL이나 비밀값이 응답·로그에 포함되지 않게 한다.
			throw new ProfileImageAccessException();
		}
	}

	private void validateConfiguration() {
		if (!StringUtils.hasText(projectUrl) || !StringUtils.hasText(secretKey) || !StringUtils.hasText(bucket)) {
			throw new ProfileImageAccessException();
		}
		URI uri = URI.create(projectUrl);
		boolean localHttp = "http".equals(uri.getScheme())
				&& ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
		if (!("https".equals(uri.getScheme()) || localHttp) || uri.getHost() == null
				|| uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
				|| (uri.getPath() != null && !uri.getPath().isEmpty())) {
			throw new ProfileImageAccessException();
		}
	}

	private record SignedUrlRequest(int expiresIn) {
	}

	private record SignedUrlResponse(String signedURL) {
	}
}
