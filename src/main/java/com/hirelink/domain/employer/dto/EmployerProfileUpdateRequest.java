package com.hirelink.domain.employer.dto;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

public class EmployerProfileUpdateRequest {

	@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
	private String name;

	@Size(max = 50, message = "직책은 50자 이하여야 합니다.")
	private String position;
	private boolean positionProvided;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setName(JsonNode value) {
		name = requireText(value);
	}

	@JsonSetter
	public void setPosition(JsonNode value) {
		positionProvided = true;
		position = value == null || value.isNull() ? null : requireText(value);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("수정할 수 없는 항목이 포함되어 있습니다.");
	}

	public boolean hasChanges() {
		return name != null || positionProvided;
	}

	public boolean hasPosition() {
		return positionProvided;
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("문자열 형식으로 입력해주세요.");
		}
		String text = value.stringValue();
		if (text.isBlank()) {
			throw new IllegalArgumentException("빈 문자열이나 공백만 입력할 수 없습니다.");
		}
		return text;
	}

	public String getName() {
		return name;
	}

	public String getPosition() {
		return position;
	}
}
