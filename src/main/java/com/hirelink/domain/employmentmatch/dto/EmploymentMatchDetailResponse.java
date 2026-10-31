package com.hirelink.domain.employmentmatch.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public record EmploymentMatchDetailResponse(
		Long matchId,
		Long applicationId,
		Long offerId,
		Long jobPostingId,
		String jobTitle,
		String workplaceName,
		String workplaceAddress,
		String wageType,
		Integer wageAmount,
		List<WorkTime> workTimes,
		LocalDate desiredStartDate,
		Instant matchedAt,
		Contact contact,
		Student student) {

	public record WorkTime(
			String dayOfWeek,
			@JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
	}

	public record Contact(String name, String phoneNumber) {
	}

	public record Student(String name, String profileImageUrl, String nationality, String visaType,
			Short topikLevel) {
	}
}
