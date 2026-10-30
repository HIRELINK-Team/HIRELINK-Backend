package com.hirelink.domain.student.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public record StudentMatchingPreferenceResponse(
		boolean businessTypeFlexible,
		List<PreferredJob> preferredJobs,
		List<PreferredWorkTime> preferredWorkTimes,
		PreferredLocation preferredLocation,
		Integer minimumHourlyWage,
		List<Priority> priorities) {

	public record PreferredJob(Long businessTypeId, String businessTypeName, String otherJobName) {
	}

	public record PreferredWorkTime(
			String dayOfWeek,
			@JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
	}

	public record PreferredLocation(String locationName, BigDecimal latitude, BigDecimal longitude, Integer radiusM) {
	}

	public record Priority(Long criterionId, String criterionName, Short priorityOrder) {
	}
}
