package com.hirelink.domain.student.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import com.hirelink.domain.student.dto.AcademicExtraction;
import com.hirelink.domain.student.dto.EnrollmentExtraction;
import com.hirelink.global.exception.StudentDocumentException;
import tools.jackson.databind.json.JsonMapper;

final class StudentDocumentOcrErrors {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private StudentDocumentOcrErrors() { }

    static StudentDocumentException unreadable(InputStream body, String type) {
        var fallback = "ENROLLMENT".equals(type) ? EnrollmentExtraction.unreadable() : AcademicExtraction.unreadable();
        try {
            byte[] bytes = body.readNBytes(1025);
            if (bytes.length > 1024) return fallback;
            var error = MAPPER.readTree(bytes);
            if (error == null || !error.isObject() || error.size() != 2
                    || !"OCR_REQUIRED_FIELDS_UNREADABLE".equals(error.path("code").asString())
                    || !error.path("fields").isArray() || error.path("fields").isEmpty()) return fallback;
            Set<String> fields = new HashSet<>();
            for (var field : error.path("fields")) {
                if (!field.isString() || !fields.add(field.stringValue())) return fallback;
            }
            if ("ENROLLMENT".equals(type) && Set.of("universityName", "gradeLevelOrSemesterCount").containsAll(fields)) {
                return EnrollmentExtraction.unreadableFields(fields.contains("universityName"), fields.contains("gradeLevelOrSemesterCount"));
            }
            if (("TRANSCRIPT".equals(type) && fields.equals(Set.of("latestSemesterAverageGpa")))
                    || ("TOPIK".equals(type) && fields.equals(Set.of("topikLevel")))) return AcademicExtraction.unreadable(type);
        } catch (IOException | RuntimeException exception) {
            // 내부 응답 원문은 공개하지 않고, 알 수 없는 응답은 기존 인식 실패로 처리한다.
        }
        return fallback;
    }
}
