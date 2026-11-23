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
		Integer source = gradeLevel != null ? gradeLevel : semesterCount;
		boolean universityUnreadable = universityName == null || universityName.isBlank() || universityName.length() > 150;
		boolean gradeUnreadable = source == null || source < 1;
		if (universityUnreadable || gradeUnreadable) { throw unreadableFields(universityUnreadable, gradeUnreadable); }
		if (campusName != null && (campusName.isBlank() || campusName.length() > 150)) { throw unreadable(); }
		long grade = gradeLevel != null ? gradeLevel : ((long) semesterCount + 1) / 2;
		if (grade > 4) {
			throw new StudentDocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_GRADE_LEVEL", "지원하는 학년 범위를 벗어났습니다");
		}
		return (short) grade;
	}

	public static StudentDocumentException unreadable() {
		return new StudentDocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_ENROLLMENT", "재학증명서 정보를 확인하지 못했습니다. 다시 촬영하거나 업로드해주세요.");
	}

	public static StudentDocumentException unreadableFields(boolean university, boolean grade) {
		if (!university && !grade) { return unreadable(); }
		String subject = university ? (grade ? "대학교명과 학년 또는 학기 수를" : "대학교명을") : "학년 또는 학기 수를";
		return new StudentDocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_ENROLLMENT",
				subject + " 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.");
	}
}
