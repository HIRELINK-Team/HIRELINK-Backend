package com.hirelink.domain.jobposting.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public record JobPostingWorkTimeResponse(
		String dayOfWeek,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
}
