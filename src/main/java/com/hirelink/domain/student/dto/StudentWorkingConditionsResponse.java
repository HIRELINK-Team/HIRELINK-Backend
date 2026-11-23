package com.hirelink.domain.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hirelink.domain.student.entity.StudyProgramType;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentWorkingConditionsResponse(
    BasicInformation basicInformation,
    @Schema(description = "저장된 근무 가능 조건을 계산한 한국 날짜") LocalDate calculatedOn,
    @Schema(description = "이번 판정에 적용한 인증대학 목록의 시작일") LocalDate accreditationEffectiveFrom,
    WorkingTimeEstimate estimate,
    @Schema(description = "예상 기준과 실제 허가를 구분하는 안내") String notice) {
    public record BasicInformation(String visaCode, StudyProgramType studyProgram, short gradeLevel,
        String universityName, @Schema(nullable = true) String campusName, short topikLevel,
        @Schema(description = "최종 성적증명서의 가장 최근 학기 평균 학점") BigDecimal previousSemesterGpa) { }
}
