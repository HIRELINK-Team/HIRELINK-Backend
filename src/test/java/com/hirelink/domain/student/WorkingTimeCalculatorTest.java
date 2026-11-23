package com.hirelink.domain.student;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.hirelink.domain.student.dto.WorkingTimeEstimate.LimitType;
import com.hirelink.domain.student.entity.StudyProgramType;
import com.hirelink.domain.student.service.WorkingTimeCalculator;
import com.hirelink.domain.student.service.AccreditedUniversityService;
import com.hirelink.global.exception.StudentDocumentException;
import static org.assertj.core.api.Assertions.*;

class WorkingTimeCalculatorTest {
    private final WorkingTimeCalculator calculator = new WorkingTimeCalculator();
    @ParameterizedTest
    @CsvSource({"ASSOCIATE,1,3,10,25", "ASSOCIATE,4,3,10,25",
        "BACHELOR,1,3,10,25", "BACHELOR,2,3,10,25", "BACHELOR,3,4,10,25", "BACHELOR,4,4,10,25",
        "MASTER,1,4,15,30", "DOCTORATE,4,4,15,30",
        "EXCHANGE,1,3,10,25", "EXCHANGE,2,3,10,25", "EXCHANGE,3,4,10,25", "EXCHANGE,4,4,10,25"})
    void basicRequirementsAndSharedLimitMatchPolicy(StudyProgramType program, short grade, short required, int shared, int weekday) {
        var unmet = calculator.calculate(program,grade,(short)(required-1),new BigDecimal("4.24"),true);
        assertThat(unmet.requiredTopikLevel()).isEqualTo(required);
        assertThat(unmet.topikRequirementMet()).isFalse();
        assertThat(unmet.combinedWeeklyLimitHours()).isEqualTo(shared);
        assertThat(unmet.additionalWeekdayHours()).isZero();
        for (var period : java.util.List.of(unmet.termWeekdays(),unmet.weekendsAndHolidays(),unmet.vacations())) {
            assertThat(period.type()).isEqualTo(LimitType.SHARED_WEEKLY_LIMIT); assertThat(period.hours()).isNull();
        }
        var met = calculator.calculate(program,grade,required,new BigDecimal("3.9999"),false);
        assertThat(met.topikRequirementMet()).isTrue(); assertThat(met.combinedWeeklyLimitHours()).isNull();
        assertThat(met.termWeekdays().hours()).isEqualTo(weekday);
        assertThat(met.termWeekdays().type()).isEqualTo(LimitType.WEEKDAY_LIMIT);
        assertThat(met.weekendsAndHolidays().type()).isEqualTo(LimitType.NO_TIME_CAP);
        assertThat(met.vacations().type()).isEqualTo(LimitType.NO_TIME_CAP);
    }
    @ParameterizedTest
    @CsvSource({"false,3.99,4,0", "true,3.99,4,5", "false,4.0,4,5", "false,3.99,5,5",
        "true,4.24,4,5", "true,3.99,5,5", "false,4.24,6,5", "true,4.24,6,5"})
    void benefitsAreAnOrConditionCappedAtFive(boolean accredited, BigDecimal gpa, short topik, int extra) {
        for (var program : java.util.List.of(StudyProgramType.BACHELOR,StudyProgramType.MASTER)) {
            var result = calculator.calculate(program,(short)3,topik,gpa,accredited);
            assertThat(result.additionalWeekdayHours()).isEqualTo(extra);
            assertThat(result.termWeekdays().hours()).isEqualTo((program==StudyProgramType.MASTER?30:25)+extra);
            assertThat(result.excellentGrades()).isEqualTo(gpa.compareTo(new BigDecimal("4.0"))>=0);
            assertThat(result.excellentKorean()).isEqualTo(topik>=5);
        }
    }
    @Test void unsupportedOrIncompleteInputsDoNotFallBackToLowerGradeRules() {
        assertThatThrownBy(()->calculator.calculate(StudyProgramType.BACHELOR,(short)5,(short)3,new BigDecimal("4"),true)).isInstanceOf(StudentDocumentException.class);
        assertThatThrownBy(()->calculator.calculate(StudyProgramType.BACHELOR,null,(short)3,new BigDecimal("4"),true)).isInstanceOf(StudentDocumentException.class);
        assertThatThrownBy(()->calculator.calculate(null,(short)2,(short)3,new BigDecimal("4"),true)).isInstanceOf(StudentDocumentException.class);
        assertThatThrownBy(()->calculator.calculate(StudyProgramType.MASTER,(short)1,(short)7,new BigDecimal("4"),true)).isInstanceOf(StudentDocumentException.class);
        assertThatThrownBy(()->calculator.calculate(StudyProgramType.MASTER,(short)1,(short)4,null,true)).isInstanceOf(StudentDocumentException.class);
    }
    @ParameterizedTest
    @CsvSource({"강원대학교(춘천캠퍼스),강원대학교", "강원대학교(강릉캠퍼스),강원대학교",
        "국립창원대학교 남해캠퍼스,국립창원대학교", "명지대학교서울캠퍼스,명지대학교",
        "가톨릭대학교(본교),가톨릭대학교", "동국대학교（WISE）,동국대학교", "한양대학교(ERICA),한양대학교",
        "한국학중앙연구원 한국학대학원,한국학중앙연구원한국학대학원"})
    void schoolComparisonIgnoresCampusAndMainCampusNotation(String raw,String normalized) {
        assertThat(AccreditedUniversityService.normalize(raw)).isEqualTo(normalized);
    }
}
