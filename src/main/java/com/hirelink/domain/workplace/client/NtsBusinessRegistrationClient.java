package com.hirelink.domain.workplace.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import com.hirelink.global.exception.BusinessRegistrationLookupException;

@Component
public class NtsBusinessRegistrationClient {

	private final String serviceKey;
	private final URI statusUri;
	private final RestClient restClient;

	@Autowired
	public NtsBusinessRegistrationClient(@Value("${business-registration.nts.service-key}") String serviceKey) {
		this(serviceKey, URI.create("https://api.odcloud.kr/api/nts-businessman/v1/status"));
	}

	NtsBusinessRegistrationClient(String serviceKey, URI statusUri) {
		this.serviceKey = serviceKey;
		this.statusUri = statusUri;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
				.followRedirects(HttpClient.Redirect.NEVER).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	public boolean isActiveBusiness(String number) {
		if (!StringUtils.hasText(serviceKey)) {
			throw new BusinessRegistrationLookupException();
		}
		try {
			URI uri = UriComponentsBuilder.fromUri(statusUri)
					.queryParam("serviceKey", "{serviceKey}").queryParam("returnType", "JSON")
					.encode().buildAndExpand(serviceKey).toUri();
			JsonNode response = restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON).body(Map.of("b_no", List.of(number)))
					.retrieve().onStatus(status -> status.value() != 200, (request, result) -> {
						throw new BusinessRegistrationLookupException();
					}).body(JsonNode.class);
			return readActiveStatus(response, number);
		} catch (RestClientException | IllegalArgumentException exception) {
			throw new BusinessRegistrationLookupException();
		}
	}

	private boolean readActiveStatus(JsonNode response, String number) {
		if (response == null || !response.path("status_code").isString()
				|| !"OK".equals(response.path("status_code").stringValue())) {
			throw new BusinessRegistrationLookupException();
		}
		JsonNode data = response.path("data");
		if (!data.isArray() || data.size() != 1) {
			throw new BusinessRegistrationLookupException();
		}
		JsonNode business = data.get(0);
		JsonNode status = business.path("b_stt_cd");
		if (!business.path("b_no").isString() || !number.equals(business.path("b_no").stringValue()) || !status.isString()) {
			throw new BusinessRegistrationLookupException();
		}
		return switch (status.stringValue()) {
			case "01" -> true;
			case "02", "03" -> false;
			case "" -> {
				if (!business.path("tax_type").isString()
						|| !"국세청에 등록되지 않은 사업자등록번호입니다".equals(business.path("tax_type").stringValue())) {
					throw new BusinessRegistrationLookupException();
				}
				yield false;
			}
			default -> throw new BusinessRegistrationLookupException();
		};
	}
}
