package com.hirelink.domain.student.dto;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import com.hirelink.global.exception.StudentDocumentException;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="성적은 가장 최근 학기 평균 학점이다. 만점 기준과 누적 평점은 저장하지 않는다. TOPIK은 급수만 추출한다.")
public record AcademicExtraction(
    @Schema(description="성적증명서의 가장 최근 학기 학년도. TOPIK에서는 null이다.", example="2025", nullable=true)
    Integer academicYear,
    @Schema(description="성적증명서의 학기. FIRST=1학기, SUMMER=여름학기, SECOND=2학기, WINTER=겨울학기. TOPIK에서는 null이다.", allowableValues={"FIRST", "SUMMER", "SECOND", "WINTER"}, nullable=true)
    String semester,
    @Schema(description="성적증명서의 가장 최근 학기 평균 학점. 만점 기준 환산 없이 반환한다. TOPIK에서는 null이다.", example="4.24", nullable=true)
    BigDecimal averageGpa,
    @Schema(description="TOPIK 등급(1~6). 성적증명서에서는 null이다.", minimum="1", maximum="6", example="4", nullable=true)
    Short topikLevel) {
    public void validate(String type) {
        boolean valid = "TRANSCRIPT".equals(type)
            ? academicYear!=null && academicYear>=1900 && academicYear<=2099 && semester!=null
              && Set.of("FIRST","SUMMER","SECOND","WINTER").contains(semester) && averageGpa!=null
              && averageGpa.signum()>=0 && averageGpa.scale()<=4 && averageGpa.compareTo(new BigDecimal("1000000"))<0 && topikLevel==null
            : "TOPIK".equals(type) && topikLevel!=null && topikLevel>=1 && topikLevel<=6 && averageGpa==null && academicYear==null && semester==null;
        if(!valid) throw unreadable(type);
    }
    public static StudentDocumentException unreadable(String type) {
        String subject = switch (type) {
            case "TRANSCRIPT" -> "직전 학기 평균 학점을";
            case "TOPIK" -> "TOPIK 등급을";
            default -> null;
        };
        if (subject == null) return unreadable();
        return new StudentDocumentException(HttpStatus.UNPROCESSABLE_ENTITY,"UNREADABLE_STUDENT_DOCUMENT",
            subject + " 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.");
    }
    public static StudentDocumentException unreadable() { return new StudentDocumentException(HttpStatus.UNPROCESSABLE_ENTITY,"UNREADABLE_STUDENT_DOCUMENT","서류 정보를 확인하지 못했습니다. 다시 촬영하거나 업로드해주세요."); }
}
