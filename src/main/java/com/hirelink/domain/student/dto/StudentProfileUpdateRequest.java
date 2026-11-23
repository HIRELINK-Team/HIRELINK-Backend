package com.hirelink.domain.student.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보낸 필드만 수정한다. 빈 요청·알 수 없는 필드·null은 400. topikLevel·universityName·gradeLevel·academicState·studyProgram·studyProgramId는 수정 대상에서 제외된다.",
		example = "{\"currentRegion\":\"서울 마포구\"}")
public class StudentProfileUpdateRequest {

	public static StudentProfileUpdateRequest forOnboarding(LocalDate birthDate, String nationality,
			String currentRegion, String koreanLevel) {
		StudentProfileUpdateRequest request = new StudentProfileUpdateRequest();
		request.birthDate = birthDate;
		request.nationality = nationality;
		request.currentRegion = currentRegion;
		request.koreanLevel = koreanLevel;
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
	@Schema(description = "등록증 인증 기록이 없는 개발용 기존 프로필에서만 입력할 수 있는 지원 세부 코드. 인증 회원은 온보딩 학업과정과 등록증으로 결정하며 직접 수정할 수 없다. 저장된 학업과정과 다른 코드로 변경할 수 없다. 생략하면 기존 값 유지, null 불가",
			allowableValues = {"D-2-1", "D-2-2", "D-2-3", "D-2-4", "D-2-6"},
			example = "D-2-2",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String visaCode;

	@Schema(description = "체류 만료일 (YYYY-MM-DD). 과거 날짜 허용, 기존 체류 시작일보다 앞설 수 없음. 생략하면 기존 값 유지, null 불가",
			example = "2027-03-01", type = "string", format = "date",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private LocalDate stayExpirationDate;

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

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("수정할 수 없는 항목이 포함되어 있습니다.");
	}

	public boolean hasChanges() {
		return name != null || birthDate != null || nationality != null || currentRegion != null
				|| koreanLevel != null || visaCode != null || stayExpirationDate != null;
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

}
