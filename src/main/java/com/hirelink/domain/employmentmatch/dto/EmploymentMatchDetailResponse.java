package com.hirelink.domain.employmentmatch.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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

	public record WorkTime(String dayOfWeek, LocalTime startTime, LocalTime endTime) {
	}

	public record Contact(String name, String phoneNumber) {
	}

	public record Student(String name, String profileImageUrl, String nationality, String visaCode,
			Short topikLevel) {
	}
}
