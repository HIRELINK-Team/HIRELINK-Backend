package com.hirelink.domain.workplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사업자등록번호의 현재 영업 상태 조회. DB에는 저장하지 않는다.")
public class BusinessRegistrationVerifyRequest {

	@NotBlank(message = "사업자등록번호를 입력해주세요.")
	@Pattern(regexp = "[0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5}",
			message = "사업자등록번호는 숫자 10자리 또는 123-45-67890 형식이어야 합니다.")
	@Schema(description = "사업자등록번호. 입력은 숫자 10자리 또는 XXX-XX-XXXXX, 응답은 하이픈 포함",
			example = "123-45-67890",
			requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
	private String businessRegistrationNumber;

	@JsonSetter
	public void setBusinessRegistrationNumber(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("사업자등록번호는 문자열로 입력해주세요.");
		}
		businessRegistrationNumber = value.stringValue().strip();
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("허용되지 않은 요청 항목입니다.");
	}

	public String getBusinessRegistrationNumber() {
		return businessRegistrationNumber;
	}
}
