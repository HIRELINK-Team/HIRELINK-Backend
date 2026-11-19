package com.hirelink.domain.student.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 마지막 마치기에서 전체 정보를 한 번에 제출한다. 경력 없음은 careers=[], TOPIK 없음은 topikLevel=null이며 두 필드 모두 생략할 수 없다. 이름 · 국적 · 체류자격 · 체류 만료일은 서버의 가입 인증 결과를 사용한다.")
public class StudentOnboardingRequest {

	@NotNull
	@Schema(description = "직접 입력한 생년월일. 한국 날짜 기준 만 18세 이상", type = "string", format = "date", example = "2000-05-14", requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDate birthDate;
	@NotBlank
	@Size(max = 150)
	@Schema(description = "현재 거주지역", example = "서울특별시 마포구", requiredMode = Schema.RequiredMode.REQUIRED)
	private String currentRegion;
	@NotBlank
	@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED")
	@Schema(description = "한국어 수준", allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"}, requiredMode = Schema.RequiredMode.REQUIRED)
	private String koreanLevel;
	@NotNull
	@Valid
	@Schema(description = "경력 목록. 경력 없음은 빈 목록을 명시한다", requiredMode = Schema.RequiredMode.REQUIRED)
	private List<@NotNull StudentCareerCreateRequest> careers;
	@Min(1)
	@Max(6)
	@Schema(description = "TOPIK 급수 1~6. 없음은 null을 명시하고, 필드 생략은 허용하지 않는다", minimum = "1", maximum = "6", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer topikLevel;
	private boolean topikLevelProvided;
	@NotNull
	@Valid
	@Schema(description = "구직조건 전체. 업종 상관없음 여부 · 업종 목록 · 최소 1개 근무시간 · 위치와 반경 · 4개 우선순위가 모두 필수. minimumHourlyWage는 선택 입력", requiredMode = Schema.RequiredMode.REQUIRED)
	private StudentOnboardingMatchingPreferenceRequest matchingPreferences;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBirthDate(JsonNode value) {
		String date = requireText(value);
		if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
			throw new IllegalArgumentException("생년월일은 YYYY-MM-DD 형식으로 입력해주세요.");
		}
		birthDate = LocalDate.parse(date);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setCurrentRegion(JsonNode value) { currentRegion = requireText(value); }

	@JsonSetter(nulls = Nulls.FAIL)
	public void setKoreanLevel(JsonNode value) { koreanLevel = requireText(value); }

	@JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
	public void setCareers(List<StudentCareerCreateRequest> value) { careers = value; }

	@JsonSetter
	public void setTopikLevel(JsonNode value) {
		topikLevelProvided = true;
		if (value == null || value.isNull()) {
			topikLevel = null;
		} else if (value.isIntegralNumber() && value.canConvertToInt()) {
			topikLevel = value.intValue();
		} else {
			throw new IllegalArgumentException("TOPIK 급수는 정수 또는 null이어야 합니다.");
		}
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setMatchingPreferences(StudentOnboardingMatchingPreferenceRequest value) { matchingPreferences = value; }

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("온보딩에서 제출할 수 없는 항목이 포함되어 있습니다.");
	}

	@AssertTrue(message = "TOPIK 급수 또는 없음을 명시해주세요.")
	@Schema(hidden = true)
	public boolean isTopikAnswerPresent() { return topikLevelProvided; }

	@AssertTrue(message = "업종 · 근무시간 · 근무위치 · 우선순위를 모두 입력해주세요. 근무시간은 최소 한 개가 필요합니다.")
	@Schema(hidden = true)
	public boolean isMatchingPreferencesComplete() {
		return matchingPreferences != null
				&& matchingPreferences.getBusinessTypeFlexible() != null
				&& matchingPreferences.getPreferredJobs() != null
				&& matchingPreferences.getPreferredWorkTimes() != null
				&& !matchingPreferences.getPreferredWorkTimes().isEmpty()
				&& matchingPreferences.getPreferredLocation() != null
				&& matchingPreferences.getPriorities() != null
				&& matchingPreferences.getPriorities().size() == 4;
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("문자열 형식으로 입력해주세요.");
		}
		return value.stringValue();
	}

	public LocalDate getBirthDate() { return birthDate; }
	public String getCurrentRegion() { return currentRegion; }
	public String getKoreanLevel() { return koreanLevel; }
	public List<StudentCareerCreateRequest> getCareers() { return careers; }
	public Integer getTopikLevel() { return topikLevel; }
	public StudentOnboardingMatchingPreferenceRequest getMatchingPreferences() { return matchingPreferences; }
}
