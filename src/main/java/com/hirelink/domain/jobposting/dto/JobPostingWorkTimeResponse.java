package com.hirelink.domain.jobposting.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용공고 근무시간")
public record JobPostingWorkTimeResponse(
		@Schema(description = "요일",
				allowableValues = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"},
				example = "MON",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String dayOfWeek,
		@JsonFormat(pattern = "HH:mm")
		@Schema(description = "시작 시간 (HH:mm), 30분 단위",
				pattern = "([01][0-9]|2[0-3]):(00|30)",
				example = "09:00", type = "string", format = "HH:mm",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		LocalTime startTime,
		@JsonFormat(pattern = "HH:mm")
		@Schema(description = "종료 시간 (HH:mm), 30분 단위. 00:00은 해당 요일의 끝인 자정. 시작과 같을 수 없음",
				pattern = "([01][0-9]|2[0-3]):(00|30)",
				example = "13:00", type = "string", format = "HH:mm",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		LocalTime endTime) {
}
