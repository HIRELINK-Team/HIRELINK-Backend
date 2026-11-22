package com.hirelink.domain.student.entity;

import java.util.Arrays;

public enum StudyProgramType {

	ASSOCIATE("전문학사", "D-2-1"),
	BACHELOR("학사", "D-2-2"),
	MASTER("석사", "D-2-3"),
	DOCTORATE("박사", "D-2-4"),
	EXCHANGE("교환학생", "D-2-6");

	private final String programName;
	private final String visaCode;

	StudyProgramType(String programName, String visaCode) {
		this.programName = programName;
		this.visaCode = visaCode;
	}

	public String getProgramName() { return programName; }
	public String getVisaCode() { return visaCode; }

	public boolean matchesRegistration(String code) {
		return "D-2".equals(code) || visaCode.equals(code);
	}

	public static boolean supportsDetailedVisa(String code) {
		return Arrays.stream(values()).anyMatch(program -> program.visaCode.equals(code));
	}

	public static boolean supportsRegistration(String code) {
		return "D-2".equals(code) || supportsDetailedVisa(code);
	}

	public static StudyProgramType fromProgramName(String name) {
		return Arrays.stream(values()).filter(program -> program.programName.equals(name)).findFirst().orElse(null);
	}
}
