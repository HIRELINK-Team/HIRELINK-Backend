package com.hirelink.domain.student.service;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.hirelink.domain.student.dto.WorkingTimeEstimate;
import com.hirelink.domain.student.dto.WorkingTimeEstimate.LimitType;
import com.hirelink.domain.student.dto.WorkingTimeEstimate.PeriodLimit;
import com.hirelink.domain.student.entity.StudyProgramType;
import com.hirelink.global.exception.StudentDocumentException;

@Component
public class WorkingTimeCalculator {
    public WorkingTimeEstimate calculate(StudyProgramType program, Short grade, Short topik, BigDecimal gpa, boolean accredited) {
        if (program == null || grade == null || grade < 1 || grade > 4 || topik == null || topik < 1 || topik > 6 || gpa == null || gpa.signum() < 0) {
            throw incomplete();
        }
        boolean graduate = program == StudyProgramType.MASTER || program == StudyProgramType.DOCTORATE;
        int required = graduate || ((program == StudyProgramType.BACHELOR || program == StudyProgramType.EXCHANGE) && grade >= 3) ? 4 : 3;
        boolean met = topik >= required;
        boolean excellentGrades = gpa.compareTo(new BigDecimal("4.0")) >= 0;
        boolean excellentKorean = topik >= 5;
        int additional = met && (accredited || excellentGrades || excellentKorean) ? 5 : 0;
        PeriodLimit shared = new PeriodLimit(LimitType.SHARED_WEEKLY_LIMIT, null);
        PeriodLimit unlimited = new PeriodLimit(LimitType.NO_TIME_CAP, null);
        return new WorkingTimeEstimate(required, met, accredited, excellentGrades, excellentKorean, additional,
                met ? null : (graduate ? 15 : 10),
                met ? new PeriodLimit(LimitType.WEEKDAY_LIMIT, (graduate ? 30 : 25) + additional) : shared,
                met ? unlimited : shared, met ? unlimited : shared);
    }
    public static StudentDocumentException incomplete() {
        return new StudentDocumentException(HttpStatus.CONFLICT, "WORKING_CONDITIONS_DATA_INCOMPLETE", "학업과정과 최종 등록 서류 정보를 확인해주세요.");
    }
}
