package com.hirelink.domain.student.dto;

import java.time.LocalDate;

import com.hirelink.domain.student.entity.KoreanLevel;

public record StudentProfileCreateResponse(
		Long studentId,
		String name,
		String phoneNumber,
		LocalDate birthDate,
		String nationality,
		String currentRegion,
		KoreanLevel koreanLevel) {
}
