package com.hirelink.domain.jobposting.dto;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Pattern;

import tools.jackson.databind.JsonNode;

public record JobPostingWorkTimeRequest(
		@Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN", message = "올바른 요일을 입력해주세요.") String dayOfWeek,
		LocalTime startTime, LocalTime endTime) {

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
