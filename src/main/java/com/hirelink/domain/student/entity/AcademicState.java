package com.hirelink.domain.student.entity;

import java.util.Set;

public enum AcademicState {
	ENROLLED,
	ON_LEAVE,
	COMPLETED,
	GRADUATED,
	IN_PROGRESS,
	DISCONTINUED,
	ON_LEAVE_OR_DISCONTINUED;

	public static Set<AcademicState> allowedFor(String visaCode) {
		if (visaCode == null) {
			return Set.of();
		}
		return switch (visaCode) {
			case "D-2-1", "D-2-2", "D-2-3", "D-2-4" -> Set.of(ENROLLED, ON_LEAVE, COMPLETED, GRADUATED);
			case "D-2-6", "D-2-8", "D-4-1", "D-4-7" -> Set.of(IN_PROGRESS, COMPLETED, DISCONTINUED);
			case "D-2-7" -> Set.of(IN_PROGRESS, ON_LEAVE_OR_DISCONTINUED, COMPLETED);
			default -> Set.of();
		};
	}
}
