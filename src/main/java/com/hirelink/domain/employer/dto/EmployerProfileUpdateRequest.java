package com.hirelink.domain.employer.dto;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이름·직책 중 보낸 항목만 수정한다. position의 null은 삭제, name의 null·빈 요청·알 수 없는 필드는 400.", example = "{\"position\":null}")
public class EmployerProfileUpdateRequest {

	@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
	@Schema(description = "회원 이름. 생략하면 기존 값 유지, null 불가", maxLength = 100,
			example = "김하늘",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String name;

	@Size(max = 50, message = "직책은 50자 이하여야 합니다.")
	@Schema(description = "직책. 미등록은 null, 빈 문자열·공백만 입력 불가. 생략하면 기존 값 유지, null이면 값 삭제", maxLength = 50,
			example = "점장",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private String position;
	@Schema(hidden = true)
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
