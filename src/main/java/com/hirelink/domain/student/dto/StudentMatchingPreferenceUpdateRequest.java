package com.hirelink.domain.student.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

public class StudentMatchingPreferenceUpdateRequest {

	private Boolean businessTypeFlexible;
	@Valid
	private List<PreferredJob> preferredJobs;
	@Valid
	private List<PreferredWorkTime> preferredWorkTimes;
	@Valid
	private PreferredLocation preferredLocation;
	@Positive(message = "최소 희망 시급은 1 이상의 정수여야 합니다.")
	private Integer minimumHourlyWage;
	private boolean minimumHourlyWageProvided;
	@Valid
	@Size(min = 4, max = 4, message = "우선순위는 네 기준을 모두 전달해주세요.")
	private List<Priority> priorities;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBusinessTypeFlexible(JsonNode value) {
		if (!value.isBoolean()) {
			throw new IllegalArgumentException("업종 상관없음 여부는 true 또는 false여야 합니다.");
		}
		businessTypeFlexible = value.booleanValue();
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setPreferredJobs(JsonNode value) {
		requireArray(value);
		List<PreferredJob> jobs = new ArrayList<>();
		for (JsonNode item : value) {
			requireObject(item, "businessTypeId", "otherJobName");
			JsonNode otherName = item.get("otherJobName");
			jobs.add(new PreferredJob(requireLong(item.get("businessTypeId")),
					otherName == null || otherName.isNull() ? null : requireText(otherName)));
		}
		preferredJobs = List.copyOf(jobs);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setPreferredWorkTimes(JsonNode value) {
		requireArray(value);
		List<PreferredWorkTime> times = new ArrayList<>();
		for (JsonNode item : value) {
			requireObject(item, "dayOfWeek", "startTime", "endTime");
			times.add(new PreferredWorkTime(requireText(item.get("dayOfWeek")),
					parseTime(item.get("startTime")), parseTime(item.get("endTime"))));
		}
		preferredWorkTimes = List.copyOf(times);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setPreferredLocation(JsonNode value) {
		requireObject(value, "locationName", "latitude", "longitude", "radiusM");
		preferredLocation = new PreferredLocation(requireText(value.get("locationName")),
				requireDecimal(value.get("latitude")), requireDecimal(value.get("longitude")), requireInt(value.get("radiusM")));
	}

	@JsonSetter
	public void setMinimumHourlyWage(JsonNode value) {
		minimumHourlyWageProvided = true;
		minimumHourlyWage = value == null || value.isNull() ? null : requireInt(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setPriorities(JsonNode value) {
		requireArray(value);
		List<Priority> values = new ArrayList<>();
		for (JsonNode item : value) {
			requireObject(item, "criterionId", "priorityOrder");
			values.add(new Priority(requireLong(item.get("criterionId")), requireInt(item.get("priorityOrder"))));
		}
		priorities = List.copyOf(values);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("수정할 수 없는 항목이 포함되어 있습니다.");
	}

	public boolean hasChanges() {
		return businessTypeFlexible != null || preferredJobs != null || preferredWorkTimes != null
				|| preferredLocation != null || minimumHourlyWageProvided || priorities != null;
	}

	public boolean hasMinimumHourlyWage() {
		return minimumHourlyWageProvided;
	}

	public Boolean getBusinessTypeFlexible() {
		return businessTypeFlexible;
	}

	public List<PreferredJob> getPreferredJobs() {
		return preferredJobs;
	}

	public List<PreferredWorkTime> getPreferredWorkTimes() {
		return preferredWorkTimes;
	}

	public PreferredLocation getPreferredLocation() {
		return preferredLocation;
	}

	public Integer getMinimumHourlyWage() {
		return minimumHourlyWage;
	}

	public List<Priority> getPriorities() {
		return priorities;
	}

	private void requireArray(JsonNode value) {
		if (value == null || !value.isArray()) {
			throw new IllegalArgumentException("목록 형식으로 입력해주세요.");
		}
	}

	private void requireObject(JsonNode value, String... allowedFields) {
		if (value == null || !value.isObject()) {
			throw new IllegalArgumentException("객체 형식으로 입력해주세요.");
		}
		if (!Set.of(allowedFields).containsAll(value.propertyNames())) {
			throw new IllegalArgumentException("수정할 수 없는 항목이 포함되어 있습니다.");
		}
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("필수 문자열을 입력해주세요.");
		}
		return value.stringValue();
	}

	private long requireLong(JsonNode value) {
		if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
			throw new IllegalArgumentException("ID는 정수여야 합니다.");
		}
		return value.longValue();
	}

	private int requireInt(JsonNode value) {
		if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new IllegalArgumentException("정수 형식으로 입력해주세요.");
		}
		return value.intValue();
	}

	private BigDecimal requireDecimal(JsonNode value) {
		if (value == null || !value.isNumber()) {
			throw new IllegalArgumentException("좌표는 숫자로 입력해주세요.");
		}
		return value.decimalValue();
	}

	private LocalTime parseTime(JsonNode value) {
		String time = requireText(value);
		if (!time.matches("([01][0-9]|2[0-3]):(00|30)")) {
			throw new IllegalArgumentException("근무시간은 HH:mm 형식의 30분 단위로 입력해주세요.");
		}
		return LocalTime.parse(time);
	}

	public record PreferredJob(
			@Positive(message = "업종 ID는 양수여야 합니다.") long businessTypeId,
			@Size(max = 100, message = "기타 업종명은 100자 이하여야 합니다.") String otherJobName) {
	}

	public record PreferredWorkTime(
			@Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN", message = "올바른 요일을 입력해주세요.") String dayOfWeek,
			LocalTime startTime, LocalTime endTime) {
	}

	public record PreferredLocation(
			@NotBlank(message = "위치 표시명을 입력해주세요.")
			@Size(max = 150, message = "위치 표시명은 150자 이하여야 합니다.") String locationName,
			@DecimalMin(value = "-90", message = "위도는 -90부터 90까지 입력해주세요.")
			@DecimalMax(value = "90", message = "위도는 -90부터 90까지 입력해주세요.")
			@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.") BigDecimal latitude,
			@DecimalMin(value = "-180", message = "경도는 -180부터 180까지 입력해주세요.")
			@DecimalMax(value = "180", message = "경도는 -180부터 180까지 입력해주세요.")
			@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.") BigDecimal longitude,
			@Positive(message = "반경은 1 이상의 정수여야 합니다.") int radiusM) {
	}

	public record Priority(
			@Positive(message = "우선순위 기준 ID는 양수여야 합니다.") long criterionId,
			@Min(value = 1, message = "우선순위는 1부터 4까지 입력해주세요.")
			@Max(value = 4, message = "우선순위는 1부터 4까지 입력해주세요.") int priorityOrder) {
	}
}
