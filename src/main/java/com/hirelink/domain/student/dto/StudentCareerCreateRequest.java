package com.hirelink.domain.student.dto;

import java.time.YearMonth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import tools.jackson.databind.JsonNode;

public class StudentCareerCreateRequest {

	@NotBlank(message = "경력명을 입력해주세요.")
	@Size(max = 150, message = "경력명은 150자 이하여야 합니다.")
	private String careerName;

	@NotNull(message = "근무 시작 월을 입력해주세요.")
	private YearMonth startMonth;

	@NotNull(message = "근무 종료 월을 입력해주세요.")
	private YearMonth endMonth;

	private String description;

	@JsonSetter
	public void setCareerName(JsonNode value) {
		careerName = requireText(value);
	}

	@JsonSetter
	public void setStartMonth(JsonNode value) {
		startMonth = parseMonth(value);
	}

	@JsonSetter
	public void setEndMonth(JsonNode value) {
		endMonth = parseMonth(value);
	}

	@JsonSetter
	public void setDescription(JsonNode value) {
		description = value == null || value.isNull() ? null : requireText(value);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("등록할 수 없는 항목이 포함되어 있습니다.");
	}

	private YearMonth parseMonth(JsonNode value) {
		String month = requireText(value);
		if (!month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
			throw new IllegalArgumentException("근무기간은 YYYY-MM 형식으로 입력해주세요.");
		}
		return YearMonth.parse(month);
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("문자열 형식으로 입력해주세요.");
		}
		return value.stringValue();
	}

	public String getCareerName() {
		return careerName;
	}

	public YearMonth getStartMonth() {
		return startMonth;
	}

	public YearMonth getEndMonth() {
		return endMonth;
	}

	public String getDescription() {
		return description;
	}
}
