package com.hirelink.domain.auth.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hirelink.global.exception.SocialAuthException;
import tools.jackson.databind.JsonNode;

@Component
public class ApickRegistrationClient {

	private final String apiKey;
	private final String endpoint;
	private final RestClient client;

	@Autowired
	public ApickRegistrationClient(@Value("${registration-verification.apick-key:}") String apiKey) {
		this(apiKey, "https://apick.app/rest/identi_card/5", Duration.ofSeconds(120));
	}

	ApickRegistrationClient(String apiKey, String endpoint, Duration timeout) {
		this.apiKey = apiKey;
		this.endpoint = endpoint;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
		factory.setReadTimeout(timeout);
		client = RestClient.builder().requestFactory(factory).build();
	}

	public void verify(String number, LocalDate issueDate, String serialNumber) {
		if (apiKey.isBlank()) {
			throw new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRATION_VERIFICATION_UNAVAILABLE", "등록증 확인 서비스를 이용할 수 없습니다.");
		}
		LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("rrn", number);
		form.add("made_date", issueDate.toString());
		if (serialNumber != null) { form.add("card_sn", serialNumber); }
		try {
			JsonNode body = client.post().uri(endpoint).header("Authorization", "Bearer " + apiKey)
					.contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.APPLICATION_JSON).body(form)
					.retrieve().onStatus(status -> status.value() != 200, (request, response) -> { throw failed(); })
					.body(JsonNode.class);
			if (body == null || !body.isObject() || body.has("error") || body.path("result").has("error")
					|| body.path("data").has("error") || !body.path("api").path("success").isBoolean()
					|| !body.path("api").path("success").booleanValue()) { throw failed(); }
			JsonNode result = body.path("data").path("result");
			JsonNode success = body.path("data").path("success");
			if (!result.isIntegralNumber() || !result.canConvertToInt() || !success.isIntegralNumber()
					|| !success.canConvertToInt() || success.intValue() != 1) { throw failed(); }
			if (result.intValue() == 0) {
				throw new SocialAuthException(HttpStatus.UNPROCESSABLE_ENTITY, "REGISTRATION_NOT_VERIFIED", "등록증 정보가 일치하지 않습니다. 등록증을 다시 촬영해주세요.");
			}
			if (result.intValue() != 1) { throw failed(); }
		} catch (RestClientException | IllegalArgumentException exception) {
			throw failed();
		}
	}

	private SocialAuthException failed() {
		return new SocialAuthException(HttpStatus.BAD_GATEWAY, "REGISTRATION_VERIFICATION_FAILED", "등록증 진위 확인을 완료하지 못했습니다. 잠시 후 다시 시도해주세요.");
	}
}
