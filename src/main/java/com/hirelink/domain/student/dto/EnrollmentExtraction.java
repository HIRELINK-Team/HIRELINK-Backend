package com.hirelink.domain.student.dto;

import org.springframework.http.HttpStatus;
import com.hirelink.global.exception.StudentDocumentException;
import io.swagger.v3.oas.annotations.media.Schema;

public record EnrollmentExtraction(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String universityName,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String campusName,
		@Schema(description = "서류에 명시된 학년. 없으면 null이며 semesterCount를 환산", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Integer gradeLevel,
		@Schema(description = "학년이 없을 때 개인의 현재·이수 학기 수. 학사 일정 제외", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Integer semesterCount) {

	public short supportedGrade() {
		if (universityName == null || universityName.isBlank() || universityName.length() > 150
				|| (campusName != null && (campusName.isBlank() || campusName.length() > 150))) { throw unreadable(); }
		Integer source = gradeLevel != null ? gradeLevel : semesterCount;
		if (source == null || source < 1) { throw unreadable(); }
		long grade = gradeLevel != null ? gradeLevel : ((long) semesterCount + 1) / 2;
		if (grade > 4) {
			throw new StudentDocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_GRADE_LEVEL", "지원하는 학년 범위를 벗어났습니다");
		}
		return (short) grade;
	}

	public static StudentDocumentException unreadable() {
		return new StudentDocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_ENROLLMENT", "재학증명서 정보를 확인하지 못했습니다. 다시 촬영하거나 업로드해주세요.");
	}
}
