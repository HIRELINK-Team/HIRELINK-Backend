package com.hirelink.domain.jobposting.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

public class JobPostingCreateRequest {

	@NotNull(message = "사업장을 선택해주세요.")
	@Positive(message = "사업장 ID는 양수여야 합니다.")
	private Long workplaceId;
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
	private List<WorkTime> workTimes = List.of();
	@NotNull(message = "공고 상태를 입력해주세요.")
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
		if (!value.isArray()) {
			throw new IllegalArgumentException("근무시간은 목록 형식으로 입력해주세요.");
		}
		List<WorkTime> times = new ArrayList<>();
		for (JsonNode item : value) {
			if (!item.isObject() || !Set.of("dayOfWeek", "startTime", "endTime").containsAll(item.propertyNames())) {
				throw new IllegalArgumentException("근무시간 항목이 올바르지 않습니다.");
			}
			times.add(new WorkTime(requireText(item.get("dayOfWeek")),
					parseTime(item.get("startTime")), parseTime(item.get("endTime"))));
		}
		workTimes = List.copyOf(times);
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

	private LocalTime parseTime(JsonNode value) {
		String text = requireText(value);
		if (!text.matches("([01][0-9]|2[0-3]):(00|30)")) {
			throw new IllegalArgumentException("근무시간은 HH:mm 형식의 30분 단위로 입력해주세요.");
		}
		return LocalTime.parse(text);
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

	public List<WorkTime> getWorkTimes() {
		return workTimes;
	}

	public JobPostingStatus getStatus() {
		return status;
	}

	public record WorkTime(
			@Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN", message = "올바른 요일을 입력해주세요.") String dayOfWeek,
			LocalTime startTime, LocalTime endTime) {
	}
}
