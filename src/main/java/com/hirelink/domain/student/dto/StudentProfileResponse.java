package com.hirelink.domain.student.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.student.entity.KoreanLevel;

public record StudentProfileResponse(
		Long studentId,
		String name,
		String email,
		String phoneNumber,
		LocalDate birthDate,
		String nationality,
		String currentRegion,
		KoreanLevel koreanLevel,
		String visaCode,
		String universityName,
		LocalDate enrollmentStartDate,
		Long studyProgramId,
		Short gradeLevel,
		String academicState,
		Short topikLevel,
		LocalDate stayStartDate,
		LocalDate stayExpirationDate,
		String profileImageUrl,
		Instant hirelinkBadgeIssuedAt,
		List<Career> careers) {

	public record Career(Long careerId, String careerName, String startMonth, String endMonth, String description) {
	}
}
