package com.hirelink.domain.student.dto;

import java.time.YearMonth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보낸 필드만 수정한다. 빈 요청 · 알 수 없는 필드 · 명시적인 null은 400. 기존 근무 내용이 비어 있으면 description을 함께 보내야 한다.", example = "{\"careerName\":\"카페 매장 근무\",\"description\":\"음료 제조 및 고객 응대\"}")
public class StudentCareerUpdateRequest {

	@Pattern(regexp = "(?s).*\\S.*", message = "경력명은 공백일 수 없습니다.")
	@Size(max = 150, message = "경력명은 150자 이하여야 합니다.")
	@Schema(description = "경력명. 빈 문자열·공백만 입력 불가. 생략하면 기존 값 유지, null 불가", maxLength = 150,
			example = "카페 아르바이트",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String careerName;

	@Schema(description = "근무 시작월 (YYYY-MM). 한국 기준 이번 달까지 허용. 생략하면 기존 값 유지, null 불가",
			pattern = "[0-9]{4}-(0[1-9]|1[0-2])",
			example = "2026-01", type = "string",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private YearMonth startMonth;
	@Schema(description = "근무 종료월 (YYYY-MM). 시작월 이상, 한국 기준 이번 달까지 허용. 생략하면 기존 값 유지, null 불가",
			pattern = "[0-9]{4}-(0[1-9]|1[0-2])",
			example = "2026-06", type = "string",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private YearMonth endMonth;
	@Pattern(regexp = "(?s).*\\S.*", message = "근무 내용을 입력해주세요.")
	@Schema(description = "근무 내용. 생략하면 기존 값 유지. null · 빈 문자열 · 공백만 입력 불가. 기존 내용이 비어 있으면 함께 입력해야 한다",
			example = "음료 제조 및 고객 응대",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String description;
	@Schema(hidden = true)
	private boolean descriptionProvided;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setCareerName(JsonNode value) {
		careerName = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setStartMonth(JsonNode value) {
		startMonth = parseMonth(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setEndMonth(JsonNode value) {
		endMonth = parseMonth(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setDescription(JsonNode value) {
		descriptionProvided = true;
		description = requireText(value);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("수정할 수 없는 항목이 포함되어 있습니다.");
	}

	public boolean hasChanges() {
		return careerName != null || startMonth != null || endMonth != null || descriptionProvided;
	}

	public boolean hasDescription() {
		return descriptionProvided;
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
