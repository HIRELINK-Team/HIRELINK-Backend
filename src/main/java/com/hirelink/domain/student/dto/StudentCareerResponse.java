package com.hirelink.domain.student.dto;

public record StudentCareerResponse(
		Long careerId,
		String careerName,
		String startMonth,
		String endMonth,
		String description) {
}
