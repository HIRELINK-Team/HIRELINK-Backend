package com.hirelink.domain.student.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 정보 최종 일괄 저장. 생략한 항목은 유지하며 null과 알 수 없는 필드는 거절한다. 서류 인증 정보는 직접 수정할 수 없다.")
public class StudentInformationUpdateRequest {

	@Schema(type = "string", format = "date", description = "한국 날짜 기준 만 18세 이상", example = "2000-03-14")
	private LocalDate birthDate;

	@Pattern(regexp = "010(?:[0-9]{8}|-[0-9]{4}-[0-9]{4})")
	@Schema(type = "string", description = "인증한 새 번호. 인증 성공 후 5분 이내 저장. 기존 번호와 같으면 재인증 불필요", example = "010-1234-5678")
	private String phoneNumber;

	@Pattern(regexp = "(?s).*\\S.*")
	@Size(max = 150)
	@Schema(type = "string", maxLength = 150, example = "서울 마포구")
	private String currentRegion;

	@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED")
	@Schema(type = "string", allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"})
	private String koreanLevel;

	@Valid
	@Schema(description = "매칭 조건 관리와 공유하는 희망 근무지역")
	private StudentMatchingPreferenceUpdateRequest.PreferredLocation preferredLocation;

	@Valid
	@Schema(description = "경력 전체 목록. 생략하면 유지하고 빈 배열이면 전체 삭제한다. 저장 후 경력 ID는 새로 부여한다.")
	private List<StudentCareerCreateRequest> careers;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBirthDate(JsonNode value) {
		birthDate = LocalDate.parse(requireText(value));
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setPhoneNumber(JsonNode value) {
		phoneNumber = requireText(value);
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
	public void setPreferredLocation(JsonNode value) {
		var request = new StudentMatchingPreferenceUpdateRequest();
		request.setPreferredLocation(value);
		preferredLocation = request.getPreferredLocation();
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setCareers(JsonNode value) {
		if (!value.isArray()) {
			throw new IllegalArgumentException("경력 목록을 입력해주세요.");
		}
		var result = new ArrayList<StudentCareerCreateRequest>();
		for (var item : value) {
			if (!item.isObject() || !Set.of("careerName", "startMonth", "endMonth", "description").containsAll(item.propertyNames())) {
				throw new IllegalArgumentException("경력 항목을 확인해주세요.");
			}
			var career = new StudentCareerCreateRequest();
			career.setCareerName(item.get("careerName"));
			career.setStartMonth(item.get("startMonth"));
			career.setEndMonth(item.get("endMonth"));
			career.setDescription(item.get("description"));
			result.add(career);
		}
		careers = List.copyOf(result);
	}

	@JsonAnySetter
	public void rejectUnknown(String field, JsonNode value) {
		throw new IllegalArgumentException("수정할 수 없는 항목입니다.");
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("문자열로 입력해주세요.");
		}
		return value.stringValue();
	}

	public boolean hasChanges() {
		return birthDate != null || phoneNumber != null || currentRegion != null || koreanLevel != null
				|| preferredLocation != null || careers != null;
	}

	public LocalDate getBirthDate() { return birthDate; }
	public String getPhoneNumber() { return phoneNumber == null ? null : phoneNumber.replace("-", ""); }
	public String getCurrentRegion() { return currentRegion; }
	public String getKoreanLevel() { return koreanLevel; }
	public StudentMatchingPreferenceUpdateRequest.PreferredLocation getPreferredLocation() { return preferredLocation; }
	public List<StudentCareerCreateRequest> getCareers() { return careers; }
}
