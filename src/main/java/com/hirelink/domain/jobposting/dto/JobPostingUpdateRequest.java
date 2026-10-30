package com.hirelink.domain.jobposting.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

public class JobPostingUpdateRequest {

	@Positive(message = "업종 ID는 양수여야 합니다.")
	private Long businessTypeId;
	@Size(max = 100, message = "직무명은 100자 이하여야 합니다.")
	private String jobRole;
	@Min(value = 1, message = "채용 인원은 1명 이상이어야 합니다.")
	@Max(value = 32767, message = "채용 인원은 32,767명 이하여야 합니다.")
	private Integer recruitmentCount;
	@Pattern(regexp = "HOURLY|MONTHLY", message = "급여 유형은 HOURLY 또는 MONTHLY여야 합니다.")
	private String wageType;
	@Positive(message = "급여는 1원 이상의 정수여야 합니다.")
	private Integer wageAmount;
	private LocalDate workStartDate;
	@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "올바른 한국어 수준을 선택해주세요.")
	private String requiredKoreanLevel;
	@Pattern(regexp = "NONE|SIX_MONTHS_OR_MORE|ONE_YEAR_OR_MORE", message = "올바른 우대 경력을 선택해주세요.")
	private String preferredExperience;
	private LocalDate applicationDeadlineDate;
	private Boolean workTimeNegotiable;
	@Valid
	private List<JobPostingWorkTimeRequest> workTimes;
	private JobPostingStatus status;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBusinessTypeId(JsonNode value) {
		if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
			throw new IllegalArgumentException("ID는 Long 범위의 정수여야 합니다.");
		}
		businessTypeId = value.longValue();
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setJobRole(JsonNode value) {
		jobRole = requireText(value);
		if (jobRole.isBlank()) {
			throw new IllegalArgumentException("직무명을 입력해주세요.");
		}
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setRecruitmentCount(JsonNode value) {
		recruitmentCount = requireInt(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setWageType(JsonNode value) {
		wageType = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setWageAmount(JsonNode value) {
		wageAmount = requireInt(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setWorkStartDate(JsonNode value) {
		workStartDate = requireDate(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setRequiredKoreanLevel(JsonNode value) {
		requiredKoreanLevel = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setPreferredExperience(JsonNode value) {
		preferredExperience = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setApplicationDeadlineDate(JsonNode value) {
		applicationDeadlineDate = requireDate(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setWorkTimeNegotiable(JsonNode value) {
		if (value == null || !value.isBoolean()) {
			throw new IllegalArgumentException("근무시간 협의 여부는 true 또는 false여야 합니다.");
		}
		workTimeNegotiable = value.booleanValue();
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setWorkTimes(JsonNode value) {
		workTimes = JobPostingWorkTimeRequest.parseList(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setStatus(JsonNode value) {
		String text = requireText(value);
		if (!text.equals("DRAFT") && !text.equals("RECRUITING")) {
			throw new IllegalArgumentException("공고 상태는 DRAFT 또는 RECRUITING이어야 합니다.");
		}
		status = JobPostingStatus.valueOf(text);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("수정할 수 없는 항목이 포함되어 있습니다.");
	}

	public boolean hasChanges() {
		return businessTypeId != null || jobRole != null || recruitmentCount != null || wageType != null
				|| wageAmount != null || workStartDate != null || requiredKoreanLevel != null || preferredExperience != null
				|| applicationDeadlineDate != null || workTimeNegotiable != null || workTimes != null || status != null;
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("문자열 형식으로 입력해주세요.");
		}
		return value.stringValue();
	}

	private Integer requireInt(JsonNode value) {
		if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new IllegalArgumentException("정수 형식으로 입력해주세요.");
		}
		return value.intValue();
	}

	private LocalDate requireDate(JsonNode value) {
		String text = requireText(value);
		if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
			throw new IllegalArgumentException("날짜는 YYYY-MM-DD 형식으로 입력해주세요.");
		}
		LocalDate date = LocalDate.parse(text);
		if (date.getYear() == 0) {
			throw new IllegalArgumentException("유효한 날짜를 입력해주세요.");
		}
		return date;
	}

	public Long getBusinessTypeId() {
		return businessTypeId;
	}

	public String getJobRole() {
		return jobRole;
	}

	public Integer getRecruitmentCount() {
		return recruitmentCount;
	}

	public String getWageType() {
		return wageType;
	}

	public Integer getWageAmount() {
		return wageAmount;
	}

	public LocalDate getWorkStartDate() {
		return workStartDate;
	}

	public String getRequiredKoreanLevel() {
		return requiredKoreanLevel;
	}

	public String getPreferredExperience() {
		return preferredExperience;
	}

	public LocalDate getApplicationDeadlineDate() {
		return applicationDeadlineDate;
	}

	public Boolean getWorkTimeNegotiable() {
		return workTimeNegotiable;
	}

	public List<JobPostingWorkTimeRequest> getWorkTimes() {
		return workTimes;
	}

	public JobPostingStatus getStatus() {
		return status;
	}
}
