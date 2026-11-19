package com.hirelink.domain.student.dto;

import java.time.YearMonth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "경력 추가. 경력명 · 시작월 · 종료월 · 근무 내용 모두 필수.")
public class StudentCareerCreateRequest {

	@NotBlank(message = "경력명을 입력해주세요.")
	@Size(max = 150, message = "경력명은 150자 이하여야 합니다.")
	@Schema(description = "경력명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "카페 아르바이트", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
	private String careerName;

	@NotNull(message = "근무 시작 월을 입력해주세요.")
	@Schema(description = "근무 시작월 (YYYY-MM). 한국 기준 이번 달까지 허용",
			pattern = "[0-9]{4}-(0[1-9]|1[0-2])",
			example = "2026-01", type = "string",
			requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
	private YearMonth startMonth;

	@NotNull(message = "근무 종료 월을 입력해주세요.")
	@Schema(description = "근무 종료월 (YYYY-MM). 시작월 이상, 한국 기준 이번 달까지 허용",
			pattern = "[0-9]{4}-(0[1-9]|1[0-2])",
			example = "2026-06", type = "string",
			requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
	private YearMonth endMonth;

	@NotBlank(message = "근무 내용을 입력해주세요.")
	@Schema(description = "필수 근무 내용. 생략 · null · 빈 문자열 · 공백만 입력 불가", example = "음료 제조 및 고객 응대", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
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
