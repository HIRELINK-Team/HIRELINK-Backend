package com.hirelink.domain.student.dto;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import com.hirelink.global.exception.StudentDocumentException;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="성적은 가장 최근 학기 평균 학점이다. 만점 기준과 누적 평점은 저장하지 않는다. TOPIK은 급수만 추출한다.")
public record AcademicExtraction(Integer academicYear,String semester,BigDecimal averageGpa,Short topikLevel) {
    public void validate(String type) {
        boolean valid = "TRANSCRIPT".equals(type)
            ? academicYear!=null && academicYear>=1900 && academicYear<=2099 && semester!=null
              && Set.of("FIRST","SUMMER","SECOND","WINTER").contains(semester) && averageGpa!=null
              && averageGpa.signum()>=0 && averageGpa.scale()<=4 && averageGpa.compareTo(new BigDecimal("1000000"))<0 && topikLevel==null
            : "TOPIK".equals(type) && topikLevel!=null && topikLevel>=1 && topikLevel<=6 && averageGpa==null && academicYear==null && semester==null;
        if(!valid) throw unreadable();
    }
    public static StudentDocumentException unreadable() { return new StudentDocumentException(HttpStatus.UNPROCESSABLE_ENTITY,"UNREADABLE_STUDENT_DOCUMENT","서류 정보를 확인하지 못했습니다. 다시 촬영하거나 업로드해주세요."); }
}
