package com.hirelink.domain.student.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

public class StudentProfileUpdateRequest {

	@Pattern(regexp = "(?s).*\\S.*", message = "이름은 공백일 수 없습니다.")
	@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
	private String name;

	private LocalDate birthDate;

	@Pattern(regexp = "[A-Z]{2}", message = "국적은 영문 대문자 2자리 국가 코드여야 합니다.")
	private String nationality;

	@Pattern(regexp = "(?s).*\\S.*", message = "현재 거주지역은 공백일 수 없습니다.")
	@Size(max = 150, message = "현재 거주지역은 150자 이하여야 합니다.")
	private String currentRegion;

	@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "올바른 한국어 수준을 입력해주세요.")
	private String koreanLevel;

	@Size(min = 1, max = 20, message = "올바른 비자 코드를 입력해주세요.")
	private String visaCode;

	private LocalDate stayExpirationDate;

	@Pattern(regexp = "(?s).*\\S.*", message = "학교명은 공백일 수 없습니다.")
	@Size(max = 150, message = "학교명은 150자 이하여야 합니다.")
	private String universityName;

	@Size(min = 1, max = 30, message = "올바른 학업 상태를 입력해주세요.")
	private String academicState;

	@Min(value = 1, message = "TOPIK 급수는 1부터 6까지 입력할 수 있습니다.")
	@Max(value = 6, message = "TOPIK 급수는 1부터 6까지 입력할 수 있습니다.")
	private Integer topikLevel;
	private boolean topikLevelProvided;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setName(JsonNode value) {
		name = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBirthDate(JsonNode value) {
		birthDate = LocalDate.parse(requireText(value));
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setNationality(JsonNode value) {
		nationality = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setCurrentRegion(JsonNode value) {
		currentRegion = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setKoreanLevel(JsonNode value) {
		koreanLevel = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setVisaCode(JsonNode value) {
		visaCode = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setStayExpirationDate(JsonNode value) {
		stayExpirationDate = LocalDate.parse(requireText(value));
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setUniversityName(JsonNode value) {
		universityName = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setAcademicState(JsonNode value) {
		academicState = requireText(value);
	}

	@JsonSetter("topikLevel")
	public void setTopikLevel(JsonNode value) {
		// 미전송은 유지, 명시적인 null은 삭제로 구분한다.
		topikLevelProvided = true;
		if (value == null || value.isNull()) {
			topikLevel = null;
		} else if (value.isIntegralNumber() && value.canConvertToInt()) {
			topikLevel = value.intValue();
		} else {
			throw new IllegalArgumentException("TOPIK 급수는 정수 또는 null이어야 합니다.");
		}
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("수정할 수 없는 항목이 포함되어 있습니다.");
	}

	public boolean hasChanges() {
		return name != null || birthDate != null || nationality != null || currentRegion != null
				|| koreanLevel != null || visaCode != null || stayExpirationDate != null || universityName != null
				|| academicState != null || topikLevelProvided;
	}

	public boolean hasTopikLevel() {
		return topikLevelProvided;
	}

	private String requireText(JsonNode value) {
		if (!value.isString()) {
			throw new IllegalArgumentException("문자열 형식으로 입력해주세요.");
		}
		return value.stringValue();
	}

	public String getName() {
		return name;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public String getNationality() {
		return nationality;
	}

	public String getCurrentRegion() {
		return currentRegion;
	}

	public String getKoreanLevel() {
		return koreanLevel;
	}

	public String getVisaCode() {
		return visaCode;
	}

	public LocalDate getStayExpirationDate() {
		return stayExpirationDate;
	}

	public String getUniversityName() {
		return universityName;
	}

	public String getAcademicState() {
		return academicState;
	}

	public Integer getTopikLevel() {
		return topikLevel;
	}
}
