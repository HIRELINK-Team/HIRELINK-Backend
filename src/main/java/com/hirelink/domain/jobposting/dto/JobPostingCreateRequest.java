package com.hirelink.domain.jobposting.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import tools.jackson.databind.JsonNode;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DRAFT는 workplaceId·status만 필수이고 나머지는 생략·null 허용. RECRUITING은 우대 경력을 제외한 모든 근무조건과 1개 이상의 근무시간 필수.",
		example = "{\"workplaceId\":1,\"status\":\"DRAFT\"}")
public class JobPostingCreateRequest {

	@NotNull(message = "사업장을 선택해주세요.")
	@Positive(message = "사업장 ID는 양수여야 합니다.")
	@Schema(description = "사업장 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
	private Long workplaceId;
	@Positive(message = "업종 ID는 양수여야 합니다.")
	@Schema(description = "업종 ID: 1 음식점·카페, 2 매장·판매, 3 사무·행사·관광, 4 통·번역, 5 면세점, 6 전공·학업, 7 인턴십, 8 기타 (DRAFT에서는 선택, RECRUITING에서는 필수)",
			example = "1",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private Long businessTypeId;
	@Size(max = 100, message = "직무명은 100자 이하여야 합니다.")
	@Schema(description = "직무. 빈 문자열·공백만 입력 불가 (DRAFT에서는 선택, RECRUITING에서는 필수)", maxLength = 100,
			example = "바리스타",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private String jobRole;
	@Min(value = 1, message = "채용 인원은 1명 이상이어야 합니다.")
	@Max(value = 32767, message = "채용 인원은 32,767명 이하여야 합니다.")
	@Schema(description = "채용 인원 (DRAFT에서는 선택, RECRUITING에서는 필수)", minimum = "1", maximum = "32767",
			example = "2",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private Integer recruitmentCount;
	@Pattern(regexp = "HOURLY|MONTHLY", message = "급여 유형은 HOURLY 또는 MONTHLY여야 합니다.")
	@Schema(description = "급여 유형 (DRAFT에서는 선택, RECRUITING에서는 필수). 값: HOURLY / MONTHLY",
			pattern = "^(HOURLY|MONTHLY)$",
			example = "HOURLY",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private String wageType;
	@Positive(message = "급여는 1원 이상의 정수여야 합니다.")
	@Schema(description = "급여 금액 (원), 1 이상의 정수 (DRAFT에서는 선택, RECRUITING에서는 필수)", minimum = "1",
			example = "13000",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private Integer wageAmount;
	@Schema(description = "근무 시작일 (YYYY-MM-DD) (DRAFT에서는 선택, RECRUITING에서는 필수)",
			example = "2027-03-01", type = "string", format = "date",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private LocalDate workStartDate;
	@Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|NONE", message = "올바른 한국어 수준을 선택해주세요.")
	@Schema(description = "요구 한국어 수준 (DRAFT에서는 선택, RECRUITING에서는 필수). 값: BEGINNER / INTERMEDIATE / ADVANCED / NONE(무관)",
			pattern = "^(BEGINNER|INTERMEDIATE|ADVANCED|NONE)$",
			example = "INTERMEDIATE",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private String requiredKoreanLevel;
	@Pattern(regexp = "NONE|SIX_MONTHS_OR_MORE|ONE_YEAR_OR_MORE", message = "올바른 우대 경력을 선택해주세요.")
	@Schema(description = "이전 클라이언트 호환용 우대 경력. 새 화면에서는 보내지 않는다. 모집 시작 시 생략·null은 NONE. 값: NONE / SIX_MONTHS_OR_MORE / ONE_YEAR_OR_MORE",
			pattern = "^(NONE|SIX_MONTHS_OR_MORE|ONE_YEAR_OR_MORE)$",
			example = "NONE",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private String preferredExperience;
	@Schema(description = "모집 마감일 (YYYY-MM-DD). 근무 시작일과의 선후 관계 제한 없음 (DRAFT에서는 선택, RECRUITING에서는 필수)",
			example = "2027-03-14", type = "string", format = "date",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private LocalDate applicationDeadlineDate;
	@Schema(description = "근무시간 협의 가능 여부 (DRAFT에서는 선택, RECRUITING에서는 필수)",
			example = "false",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private Boolean workTimeNegotiable;
	@Valid
	@Schema(description = "근무시간 목록. DRAFT에서는 생략·null·[] 허용, RECRUITING에서는 1개 이상 필수",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private List<JobPostingWorkTimeRequest> workTimes = List.of();
	@NotNull(message = "공고 상태를 입력해주세요.")
	@Schema(description = "공고 상태",
			allowableValues = {"DRAFT", "RECRUITING"},
			example = "RECRUITING",
			requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
	private JobPostingStatus status;

	@JsonSetter
	public void setWorkplaceId(JsonNode value) {
		workplaceId = nullableLong(value);
	}

	@JsonSetter
	public void setBusinessTypeId(JsonNode value) {
		businessTypeId = nullableLong(value);
	}

	@JsonSetter
	public void setJobRole(JsonNode value) {
		jobRole = nullableText(value);
		if (jobRole != null && jobRole.isBlank()) {
			throw new IllegalArgumentException("직무명을 입력해주세요.");
		}
	}

	@JsonSetter
	public void setRecruitmentCount(JsonNode value) {
		recruitmentCount = nullableInt(value);
	}

	@JsonSetter
	public void setWageType(JsonNode value) {
		wageType = nullableText(value);
	}

	@JsonSetter
	public void setWageAmount(JsonNode value) {
		wageAmount = nullableInt(value);
	}

	@JsonSetter
	public void setWorkStartDate(JsonNode value) {
		workStartDate = nullableDate(value);
	}

	@JsonSetter
	public void setRequiredKoreanLevel(JsonNode value) {
		requiredKoreanLevel = nullableText(value);
	}

	@JsonSetter
	public void setPreferredExperience(JsonNode value) {
		preferredExperience = nullableText(value);
	}

	@JsonSetter
	public void setApplicationDeadlineDate(JsonNode value) {
		applicationDeadlineDate = nullableDate(value);
	}

	@JsonSetter
	public void setWorkTimeNegotiable(JsonNode value) {
		if (value == null || value.isNull()) {
			workTimeNegotiable = null;
			return;
		}
		if (!value.isBoolean()) {
			throw new IllegalArgumentException("근무시간 협의 여부는 true 또는 false여야 합니다.");
		}
		workTimeNegotiable = value.booleanValue();
	}

	@JsonSetter
	public void setWorkTimes(JsonNode value) {
		if (value == null || value.isNull()) {
			workTimes = List.of();
			return;
		}
		workTimes = JobPostingWorkTimeRequest.parseList(value);
	}

	@JsonSetter
	public void setStatus(JsonNode value) {
		String text = nullableText(value);
		if (text != null && !text.equals("DRAFT") && !text.equals("RECRUITING")) {
			throw new IllegalArgumentException("공고 상태는 DRAFT 또는 RECRUITING이어야 합니다.");
		}
		status = text == null ? null : JobPostingStatus.valueOf(text);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("등록할 수 없는 항목이 포함되어 있습니다.");
	}

	private String nullableText(JsonNode value) {
		return value == null || value.isNull() ? null : requireText(value);
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("문자열 형식으로 입력해주세요.");
		}
		return value.stringValue();
	}

	private Long nullableLong(JsonNode value) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isIntegralNumber() || !value.canConvertToLong()) {
			throw new IllegalArgumentException("ID는 정수여야 합니다.");
		}
		return value.longValue();
	}

	private Integer nullableInt(JsonNode value) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new IllegalArgumentException("정수 형식으로 입력해주세요.");
		}
		return value.intValue();
	}

	private LocalDate nullableDate(JsonNode value) {
		String text = nullableText(value);
		if (text == null) {
			return null;
		}
		if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
			throw new IllegalArgumentException("날짜는 YYYY-MM-DD 형식으로 입력해주세요.");
		}
		LocalDate date = LocalDate.parse(text);
		if (date.getYear() == 0) {
			throw new IllegalArgumentException("유효한 날짜를 입력해주세요.");
		}
		return date;
	}

	public Long getWorkplaceId() {
		return workplaceId;
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
