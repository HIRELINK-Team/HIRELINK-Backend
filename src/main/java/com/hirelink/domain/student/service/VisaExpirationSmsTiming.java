package com.hirelink.domain.student.service;

import java.time.LocalDate;

enum VisaExpirationSmsTiming {

	MONTH("[HIRELINK] 비자 만료 1개월 전입니다. 서비스에서 정보를 확인해 주세요.", null),
	WEEK("[HIRELINK] 비자 만료 1주일 전입니다. 서비스에서 정보를 확인해 주세요.",
			"[HIRELINK] 채용한 유학생의 비자 만료 1주일 전입니다. 서비스에서 확인해 주세요."),
	DAY("[HIRELINK] 오늘 비자가 만료됩니다. 서비스에서 정보를 확인해 주세요.",
			"[HIRELINK] 채용한 유학생의 비자가 오늘 만료됩니다. 서비스에서 확인해 주세요.");

	private final String studentMessage;
	private final String employerMessage;

	VisaExpirationSmsTiming(String studentMessage, String employerMessage) {
		this.studentMessage = studentMessage;
		this.employerMessage = employerMessage;
	}

	static VisaExpirationSmsTiming dueOn(LocalDate expiryDate, LocalDate today) {
		if (expiryDate.minusMonths(1).equals(today)) {
			return MONTH;
		}
		if (expiryDate.minusWeeks(1).equals(today)) {
			return WEEK;
		}
		if (expiryDate.equals(today)) {
			return DAY;
		}
		return null;
	}

	String studentMessage() {
		return studentMessage;
	}

	String employerMessage() {
		return employerMessage;
	}

	boolean notifiesEmployer() {
		return employerMessage != null;
	}
}
