package com.hirelink.domain.auth.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public class SocialSignupBirthDateRequest {

	@NotNull
	@Schema(description = "직접 입력한 생년월일. 한국 날짜 기준 만 18세 이상", type = "string", format = "date",
			example = "2000-05-14", requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDate birthDate;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBirthDate(JsonNode value) {
		if (value == null || !value.isString() || !value.stringValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
			throw new IllegalArgumentException("생년월일은 YYYY-MM-DD 형식으로 입력해주세요.");
		}
		birthDate = LocalDate.parse(value.stringValue());
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("생년월일만 제출할 수 있습니다.");
	}

	public LocalDate getBirthDate() { return birthDate; }
}
