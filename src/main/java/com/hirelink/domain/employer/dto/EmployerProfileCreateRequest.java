package com.hirelink.domain.employer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import tools.jackson.databind.JsonNode;

public class EmployerProfileCreateRequest {

	@NotBlank(message = "이름을 입력해주세요.")
	@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
	private String name;

	@NotBlank(message = "유입 경로를 입력해주세요.")
	@Pattern(regexp = "SEARCH|REFERRAL|OTHER",
			message = "유입 경로는 SEARCH, REFERRAL, OTHER 중 하나여야 합니다.")
	private String acquisitionChannel;

	@JsonSetter
	public void setName(JsonNode value) {
		name = requireText(value);
	}

	@JsonSetter
	public void setAcquisitionChannel(JsonNode value) {
		acquisitionChannel = requireText(value);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("허용되지 않은 요청 항목입니다.");
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("문자열 형식으로 입력해주세요.");
		}
		return value.stringValue();
	}

	public String getName() {
		return name;
	}

	public String getAcquisitionChannel() {
		return acquisitionChannel;
	}
}
