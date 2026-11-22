package com.hirelink.domain.student;

import org.junit.jupiter.api.Test;
import com.hirelink.domain.student.dto.EnrollmentExtraction;
import com.hirelink.global.exception.StudentDocumentException;
import static org.assertj.core.api.Assertions.*;

class EnrollmentExtractionTest {
	@Test void usesExplicitYearBeforeSemesterCount() {
		for (int year = 1; year <= 4; year++) { assertThat(new EnrollmentExtraction("대학교", null, year, 9).supportedGrade()).isEqualTo((short) year); }
	}
	@Test void roundsUpSemestersWithoutAddingNextSemester() {
		short[] expected = {1,1,2,2,3,3,4,4};
		for (int count = 1; count <= 8; count++) { assertThat(new EnrollmentExtraction("대학교", "캠퍼스", null, count).supportedGrade()).isEqualTo(expected[count - 1]); }
	}
	@Test void rejectsAboveUpperBoundIncludingIntegerOverflowWithoutChangingMessage() {
		for (var extraction : new EnrollmentExtraction[] {new EnrollmentExtraction("대학교", null, 5, 1),
				new EnrollmentExtraction("대학교", null, null, 9), new EnrollmentExtraction("대학교", null, null, Integer.MAX_VALUE)}) {
			assertThatThrownBy(extraction::supportedGrade).isInstanceOf(StudentDocumentException.class)
					.hasMessage("지원하는 학년 범위를 벗어났습니다");
		}
	}
	@Test void rejectsUnknownAndNonPositiveCounts() {
		for (Integer count : new Integer[] {null, 0, -1}) {
			assertThatThrownBy(() -> new EnrollmentExtraction("대학교", null, null, count).supportedGrade())
					.isInstanceOf(StudentDocumentException.class).extracting("code").isEqualTo("UNREADABLE_ENROLLMENT");
		}
	}
}
