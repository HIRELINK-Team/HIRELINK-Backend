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

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보낸 필드만 수정한다. 빈 요청·알 수 없는 필드·topikLevel 외 null은 400. studyProgramId와 gradeLevel은 수정 대상에서 제외된다.",
		example = "{\"visaCode\":\"D-2\",\"topikLevel\":null}")
public class StudentProfileUpdateRequest {

	public static StudentProfileUpdateRequest forOnboarding(LocalDate birthDate, String nationality,
			String currentRegion, String koreanLevel, String visaCode, LocalDate stayExpirationDate, Integer topikLevel) {
		StudentProfileUpdateRequest request = new StudentProfileUpdateRequest();
		request.birthDate = birthDate;
		request.nationality = nationality;
		request.currentRegion = currentRegion;
		request.koreanLevel = koreanLevel;
		request.visaCode = visaCode;
		request.stayExpirationDate = stayExpirationDate;
		request.topikLevel = topikLevel;
		request.topikLevelProvided = true;
		return request;
	}

	@Pattern(regexp = "(?s).*\\S.*", message = "이름은 공백일 수 없습니다.")
	@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
	@Schema(description = "회원 이름. 생략하면 기존 값 유지, null 불가", maxLength = 100,
			example = "김하늘",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String name;

	@Schema(description = "생년월일 (YYYY-MM-DD). 한국 기준 만 18세 이상. 생략하면 기존 값 유지, null 불가",
			example = "2000-05-14", type = "string", format = "date",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private LocalDate birthDate;

	@Pattern(regexp = "[A-Z]{2}", message = "국적은 영문 대문자 2자리 국가 코드여야 합니다.")
	@Schema(description = "ISO 3166-1 alpha-2 국가 코드 (영문 대문자 2자리). 생략하면 기존 값 유지, null 불가",
			pattern = "[A-Z]{2}",
			example = "VN",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String nationality;

	@Pattern(regexp = "(?s).*\\S.*", message = "현재 거주지역은 공백일 수 없습니다.")
	@Size(max = 150, message = "현재 거주지역은 150자 이하여야 합니다.")
	@Schema(description = "현재 거주지역. 빈 문자열·공백만 입력 불가. 생략하면 기존 값 유지, null 불가", maxLength = 150,
			example = "서울특별시 성동구",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String currentRegion;

	@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "올바른 한국어 수준을 입력해주세요.")
	@Schema(description = "한국어 수준. 화면에서는 초급·중급·고급으로 변환 가능. 생략하면 기존 값 유지, null 불가",
			allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"},
			example = "INTERMEDIATE",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String koreanLevel;

	@Size(min = 1, max = 20, message = "올바른 비자 코드를 입력해주세요.")
	@Schema(description = "체류자격 코드. D-2, D-4 상위 코드와 기존 세부 코드를 지원한다. 학업 상태가 없으면 코드만 전송할 수 있다. 생략하면 기존 값 유지, null 불가",
			allowableValues = {"D-2", "D-4", "D-2-1", "D-2-2", "D-2-3", "D-2-4", "D-2-6", "D-2-7", "D-2-8", "D-4-1", "D-4-7"},
			example = "D-2",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String visaCode;

	@Schema(description = "체류 만료일 (YYYY-MM-DD). 과거 날짜 허용, 기존 체류 시작일보다 앞설 수 없음. 생략하면 기존 값 유지, null 불가",
			example = "2027-03-01", type = "string", format = "date",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private LocalDate stayExpirationDate;

	@Pattern(regexp = "(?s).*\\S.*", message = "학교명은 공백일 수 없습니다.")
	@Size(max = 150, message = "학교명은 150자 이하여야 합니다.")
	@Schema(description = "학교명. 빈 문자열·공백만 입력 불가. 생략하면 기존 값 유지, null 불가", maxLength = 150,
			example = "예시대학교",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String universityName;

	@Size(min = 1, max = 30, message = "올바른 학업 상태를 입력해주세요.")
	@Schema(description = "선택 입력인 학업 상태. D-2는 기존 D-2 세부 코드의 상태 전체를 허용하고 D-4는 IN_PROGRESS, COMPLETED, DISCONTINUED를 허용한다. D-2-1/2/3/4: ENROLLED, ON_LEAVE, COMPLETED, GRADUATED; D-2-6/8, D-4-1/7: IN_PROGRESS, COMPLETED, DISCONTINUED; D-2-7: IN_PROGRESS, ON_LEAVE_OR_DISCONTINUED, COMPLETED. 생략하면 기존 값 유지, null 불가",
			allowableValues = {"ENROLLED", "ON_LEAVE", "COMPLETED", "GRADUATED", "IN_PROGRESS", "DISCONTINUED", "ON_LEAVE_OR_DISCONTINUED"},
			example = "ENROLLED",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String academicState;

	@Min(value = 1, message = "TOPIK 급수는 1부터 6까지 입력할 수 있습니다.")
	@Max(value = 6, message = "TOPIK 급수는 1부터 6까지 입력할 수 있습니다.")
	@Schema(description = "TOPIK 급수. 미등록은 null. 생략하면 기존 값 유지, null이면 값 삭제", minimum = "1", maximum = "6",
			example = "4",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private Integer topikLevel;
	@Schema(hidden = true)
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
