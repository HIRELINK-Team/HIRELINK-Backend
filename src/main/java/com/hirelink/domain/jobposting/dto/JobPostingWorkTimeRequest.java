package com.hirelink.domain.jobposting.dto;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Pattern;

import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "요일별 근무시간. 자정 넘는 구간은 두 요일로 분리, 같은 요일 겹침 불가. 인접 구간 허용.")
public record JobPostingWorkTimeRequest(
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

	public static List<JobPostingWorkTimeRequest> parseList(JsonNode value) {
		if (value == null || !value.isArray()) {
			throw new IllegalArgumentException("근무시간은 목록 형식으로 입력해주세요.");
		}
		List<JobPostingWorkTimeRequest> times = new ArrayList<>();
		for (JsonNode item : value) {
			if (!item.isObject() || !Set.of("dayOfWeek", "startTime", "endTime").containsAll(item.propertyNames())) {
				throw new IllegalArgumentException("근무시간 항목이 올바르지 않습니다.");
			}
			times.add(new JobPostingWorkTimeRequest(requireText(item.get("dayOfWeek")),
					parseTime(item.get("startTime")), parseTime(item.get("endTime"))));
		}
		return List.copyOf(times);
	}

	private static String requireText(JsonNode value) {
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("문자열 형식으로 입력해주세요.");
		}
		return value.stringValue();
	}

	private static LocalTime parseTime(JsonNode value) {
		String text = requireText(value);
		if (!text.matches("([01][0-9]|2[0-3]):(00|30)")) {
			throw new IllegalArgumentException("근무시간은 HH:mm 형식의 30분 단위로 입력해주세요.");
		}
		return LocalTime.parse(text);
	}
}
