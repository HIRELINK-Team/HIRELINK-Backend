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

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보낸 항목만 수정한다. null·빈 요청·알 수 없는 필드는 400. 사업장 변경 불가. workTimes는 전체 교체.", example = "{\"wageAmount\":14000}")
public class JobPostingUpdateRequest {

	@Positive(message = "업종 ID는 양수여야 합니다.")
	@Schema(description = "업종 ID: 1 음식점·카페, 2 매장·판매, 3 사무·행사·관광, 4 통·번역, 5 면세점, 6 전공·학업, 7 인턴십, 8 기타. 생략하면 기존 값 유지, null 불가",
			example = "1",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private Long businessTypeId;
	@Size(max = 100, message = "직무명은 100자 이하여야 합니다.")
	@Schema(description = "직무. 빈 문자열·공백만 입력 불가. 생략하면 기존 값 유지, null 불가", maxLength = 100,
			example = "바리스타",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String jobRole;
	@Min(value = 1, message = "채용 인원은 1명 이상이어야 합니다.")
	@Max(value = 32767, message = "채용 인원은 32,767명 이하여야 합니다.")
	@Schema(description = "채용 인원. 생략하면 기존 값 유지, null 불가", minimum = "1", maximum = "32767",
			example = "2",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private Integer recruitmentCount;
	@Pattern(regexp = "HOURLY|MONTHLY", message = "급여 유형은 HOURLY 또는 MONTHLY여야 합니다.")
	@Schema(description = "급여 유형. 생략하면 기존 값 유지, null 불가",
			allowableValues = {"HOURLY", "MONTHLY"},
			example = "HOURLY",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String wageType;
	@Positive(message = "급여는 1원 이상의 정수여야 합니다.")
	@Schema(description = "급여 금액 (원), 1 이상의 정수. 생략하면 기존 값 유지, null 불가", minimum = "1",
			example = "13000",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private Integer wageAmount;
	@Schema(description = "근무 시작일 (YYYY-MM-DD). 생략하면 기존 값 유지, null 불가",
			example = "2027-03-01", type = "string", format = "date",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private LocalDate workStartDate;
	@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "올바른 한국어 수준을 선택해주세요.")
	@Schema(description = "요구 한국어 수준. 생략하면 기존 값 유지, null 불가",
			allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"},
			example = "INTERMEDIATE",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String requiredKoreanLevel;
	@Pattern(regexp = "NONE|SIX_MONTHS_OR_MORE|ONE_YEAR_OR_MORE", message = "올바른 우대 경력을 선택해주세요.")
	@Schema(description = "우대 경력. 생략하면 기존 값 유지, null 불가",
			allowableValues = {"NONE", "SIX_MONTHS_OR_MORE", "ONE_YEAR_OR_MORE"},
			example = "NONE",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String preferredExperience;
	@Schema(description = "모집 마감일 (YYYY-MM-DD). 근무 시작일과의 선후 관계 제한 없음. 생략하면 기존 값 유지, null 불가",
			example = "2027-03-14", type = "string", format = "date",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private LocalDate applicationDeadlineDate;
	@Schema(description = "근무시간 협의 가능 여부. 생략하면 기존 값 유지, null 불가", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private Boolean workTimeNegotiable;
	@Valid
	@Schema(description = "근무시간 목록. 월~일, 시작 시간 오름차순. 없으면 []. 생략하면 기존 값 유지, null 불가", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private List<JobPostingWorkTimeRequest> workTimes;
	@Schema(description = "공고 상태. 생략하면 기존 값 유지, null 불가",
			allowableValues = {"DRAFT", "RECRUITING"},
			example = "RECRUITING",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
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
