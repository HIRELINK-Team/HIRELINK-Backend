package com.hirelink.domain.employmentmatch.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EmploymentMatchListResponse(List<Item> matches) {

	public record Item(
			Long matchId,
			Long applicationId,
			Long offerId,
			Long jobPostingId,
			String jobTitle,
			String workplaceName,
			String wageType,
			Integer wageAmount,
			LocalDate desiredStartDate,
			Instant matchedAt,
			Student student) {
	}

	public record Student(String name, String profileImageUrl, String nationality, String visaType,
			Short topikLevel) {
	}
}
