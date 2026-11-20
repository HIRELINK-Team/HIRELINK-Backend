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

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보낸 항목만 수정한다. 목록은 전체 교체. businessTypeFlexible과 preferredJobs는 함께 전송한다. minimumHourlyWage 외 null은 허용하지 않는다.",
		example = """
				{
				  "businessTypeFlexible": false,
				  "preferredJobs": [{"businessTypeId": 1}],
				  "preferredWorkTimes": [{"dayOfWeek": "MON", "startTime": "09:00", "endTime": "13:00"}],
				  "preferredLocation": {
				    "locationName": "서울특별시 성동구", "latitude": 37.5636,
				    "longitude": 127.0365, "radiusM": 500
				  },
				  "minimumHourlyWage": 12000,
				  "priorities": [
				    {"criterionId": 1, "priorityOrder": 1},
				    {"criterionId": 2, "priorityOrder": 2},
				    {"criterionId": 3, "priorityOrder": 3},
				    {"criterionId": 4, "priorityOrder": 4}
				  ]
				}
				""")
public class StudentMatchingPreferenceUpdateRequest {

	@Schema(description = "업종 상관없음 여부. 미설정 시 false. 생략하면 기존 값 유지, null 불가",
			example = "false",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private Boolean businessTypeFlexible;
	@Valid
	@Schema(description = "희망 업종 목록. 전체 교체. businessTypeFlexible=true이면 [], false이면 1개 이상. 생략하면 기존 값 유지, null 불가", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private List<PreferredJob> preferredJobs;
	@Valid
	@Schema(description = "희망 근무시간 목록. 요청 시 전체 교체, []는 모두 삭제. 생략하면 기존 값 유지, null 불가", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private List<PreferredWorkTime> preferredWorkTimes;
	@Valid
	@Schema(description = "희망 근무지역. 전송하면 지역명·위도·경도·반경 모두 필수. 생략하면 기존 값 유지, null 불가",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private PreferredLocation preferredLocation;
	@Positive(message = "최소 희망 시급은 1 이상의 정수여야 합니다.")
	@Schema(description = "희망 최소 시급 (원). 1 이상의 정수, null은 미설정. 생략하면 기존 값 유지, null이면 값 삭제", minimum = "1",
			example = "12000",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	private Integer minimumHourlyWage;
	@Schema(hidden = true)
	private boolean minimumHourlyWageProvided;
	@Valid
	@Size(min = 4, max = 4, message = "우선순위는 네 기준을 모두 전달해주세요.")
	@Schema(description = "우선순위 목록. 네 기준 모두를 중복 없이 전송하여 전체 교체. 생략하면 기존 값 유지, null 불가",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
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
		int radiusM = requireInt(value.get("radiusM"));
		if (radiusM % 100 != 0) {
			throw new IllegalArgumentException("반경은 100m 간격으로 입력해주세요.");
		}
		preferredLocation = new PreferredLocation(requireText(value.get("locationName")),
				requireDecimal(value.get("latitude")), requireDecimal(value.get("longitude")), radiusM);
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

	@Schema(name = "StudentMatchingPreferenceUpdateRequestPreferredJob")
	public record PreferredJob(
			@Positive(message = "업종 ID는 양수여야 합니다.")
			@Schema(description = "업종 ID: 1 음식점·카페, 2 매장·판매, 3 사무·행사·관광, 4 통·번역, 5 면세점, 6 전공·학업, 7 인턴십, 8 기타", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			long businessTypeId,
			@Size(max = 100, message = "기타 업종명은 100자 이하여야 합니다.")
			@Schema(description = "OTHER 업종이면 필수인 기타 업종명. 다른 업종에서는 null 또는 생략", maxLength = 100, example = "영화관", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
			String otherJobName) {
	}

	@Schema(name = "StudentMatchingPreferenceUpdateRequestPreferredWorkTime")
	public record PreferredWorkTime(
			@Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN", message = "올바른 요일을 입력해주세요.")
			@Schema(description = "요일", allowableValues = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}, example = "MON", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String dayOfWeek,
			@Schema(description = "시작 시간 (HH:mm), 30분 단위",
					pattern = "([01][0-9]|2[0-3]):(00|30)",
					example = "09:00", type = "string", format = "HH:mm",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			LocalTime startTime,
			@Schema(description = "종료 시간 (HH:mm), 30분 단위. 00:00은 해당 요일의 끝인 자정. 시작과 같을 수 없음",
					pattern = "([01][0-9]|2[0-3]):(00|30)",
					example = "13:00", type = "string", format = "HH:mm",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			LocalTime endTime) {
	}

	@Schema(name = "StudentMatchingPreferenceUpdateRequestPreferredLocation")
	public record PreferredLocation(
			@NotBlank(message = "위치 표시명을 입력해주세요.")
			@Size(max = 150, message = "위치 표시명은 150자 이하여야 합니다.")
			@Schema(description = "희망 지역명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "서울특별시 성동구", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String locationName,
			@DecimalMin(value = "-90", message = "위도는 -90부터 90까지 입력해주세요.")
			@DecimalMax(value = "90", message = "위도는 -90부터 90까지 입력해주세요.")
			@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.")
			@Schema(description = "위도. 소수점 이하 최대 7자리", minimum = "-90", maximum = "90", example = "37.5665", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			BigDecimal latitude,
			@DecimalMin(value = "-180", message = "경도는 -180부터 180까지 입력해주세요.")
			@DecimalMax(value = "180", message = "경도는 -180부터 180까지 입력해주세요.")
			@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.")
			@Schema(description = "경도. 소수점 이하 최대 7자리", minimum = "-180", maximum = "180", example = "126.978", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			BigDecimal longitude,
			@Min(value = 100, message = "반경은 100~1,000m 사이여야 합니다.")
			@Max(value = 1000, message = "반경은 100~1,000m 사이여야 합니다.")
			@Schema(description = "희망 근무 반경 (미터). 100~1,000 사이의 정수, 100m 간격", minimum = "100", maximum = "1000", multipleOf = 100, example = "500", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			int radiusM) {
	}

	@Schema(name = "StudentMatchingPreferenceUpdateRequestPriority")
	public record Priority(
			@Positive(message = "우선순위 기준 ID는 양수여야 합니다.")
			@Schema(description = "우선순위 기준 ID: 1 근무시간, 2 시급, 3 거리, 4 업종", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			long criterionId,
			@Min(value = 1, message = "우선순위는 1부터 4까지 입력해주세요.")
			@Max(value = 4, message = "우선순위는 1부터 4까지 입력해주세요.")
			@Schema(description = "순위. 1~4를 각각 한 번 사용", minimum = "1", maximum = "4", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			int priorityOrder) {
	}
}
