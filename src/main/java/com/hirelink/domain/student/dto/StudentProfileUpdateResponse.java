package com.hirelink.domain.student.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.hirelink.domain.student.entity.KoreanLevel;

public record StudentProfileUpdateResponse(
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
		Long studyProgramId,
		Short gradeLevel,
		String academicState,
		Short topikLevel,
		LocalDate stayExpirationDate,
		Instant hirelinkBadgeIssuedAt) {
}
