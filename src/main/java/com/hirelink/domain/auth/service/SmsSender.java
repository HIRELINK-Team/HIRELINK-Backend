package com.hirelink.domain.auth.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import com.hirelink.global.exception.SmsDeliveryException;

@Component
public class SmsSender {

	private final String apiKey;
	private final String apiSecret;
	private final String senderNumber;
	private final URI sendUri;
	private final RestClient restClient;
	private final SecureRandom secureRandom = new SecureRandom();

	@Autowired
	public SmsSender(@Value("${sms.solapi.api-key}") String apiKey,
			@Value("${sms.solapi.api-secret}") String apiSecret,
			@Value("${sms.solapi.sender-number}") String senderNumber) {
		this(apiKey, apiSecret, senderNumber, URI.create("https://api.solapi.com/messages/v4/send-many/detail"));
	}

	SmsSender(String apiKey, String apiSecret, String senderNumber, URI sendUri) {
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
		this.senderNumber = senderNumber;
		this.sendUri = sendUri;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	public void sendVerificationCode(String phoneNumber, String code) {
		if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret) || !StringUtils.hasText(senderNumber)) {
			throw new SmsDeliveryException();
		}
		try {
			Map<String, String> message = Map.of("to", phoneNumber, "from", senderNumber, "type", "SMS",
					"text", "[HIRELINK] 인증번호 [" + code + "]");
			JsonNode response = restClient.post().uri(sendUri).contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, createAuthorization())
					.body(Map.of("messages", List.of(message)))
					.retrieve().onStatus(status -> status.value() != 200, (request, result) -> {
						throw new SmsDeliveryException();
					}).body(JsonNode.class);
			validateAcceptance(response);
		} catch (GeneralSecurityException | RestClientException | IllegalArgumentException exception) {
			throw new SmsDeliveryException();
		}
	}

	private String createAuthorization() throws GeneralSecurityException {
		String date = Instant.now().toString();
		byte[] randomBytes = new byte[16];
		secureRandom.nextBytes(randomBytes);
		String salt = HexFormat.of().formatHex(randomBytes);
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
		return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
	}

	private void validateAcceptance(JsonNode response) {
		if (response == null) {
			throw new SmsDeliveryException();
		}
		JsonNode count = response.path("groupInfo").path("count");
		JsonNode failures = response.path("failedMessageList");
		if (!count.path("total").isIntegralNumber() || count.path("total").longValue() != 1
				|| !count.path("registeredSuccess").isIntegralNumber() || count.path("registeredSuccess").longValue() != 1
				|| !count.path("registeredFailed").isIntegralNumber() || count.path("registeredFailed").longValue() != 0
				|| !failures.isArray() || !failures.isEmpty()) {
			throw new SmsDeliveryException();
		}
	}
}
