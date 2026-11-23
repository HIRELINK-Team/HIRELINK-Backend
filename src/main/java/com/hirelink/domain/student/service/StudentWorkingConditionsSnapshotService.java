package com.hirelink.domain.student.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.student.dto.StudentWorkingConditionsResponse;
import com.hirelink.domain.student.dto.StudentWorkingConditionsResponse.BasicInformation;
import com.hirelink.domain.student.entity.StudentEnrollmentDocument;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.entity.StudyProgramType;
import com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository;
import com.hirelink.domain.student.repository.StudyProgramRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class StudentWorkingConditionsSnapshotService {
    private final ObjectMapper mapper;
    private final Clock clock;
    private final StudyProgramRepository programs;
    private final StudentEnrollmentDocumentRepository documents;
    private final AccreditedUniversityService universities;
    private final WorkingTimeCalculator calculator;

    public StudentWorkingConditionsSnapshotService(StudyProgramRepository programs,
            StudentEnrollmentDocumentRepository documents, AccreditedUniversityService universities,
            WorkingTimeCalculator calculator, ObjectMapper mapper, Clock clock) {
        this.programs = programs; this.documents = documents; this.universities = universities;
        this.calculator = calculator; this.mapper = mapper; this.clock = clock;
    }

    // 최종 서류 등록·갱신과 동일하게 호출자가 회원 행을 잠근 상태에서 저장한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public StudentWorkingConditionsResponse refresh(StudentProfile profile) {
        var program = profile.getStudyProgramId() == null ? null : programs.findById(profile.getStudyProgramId())
                .map(value -> StudyProgramType.fromProgramName(value.getProgramName())).orElse(null);
        if (program == null || !program.getVisaCode().equals(profile.getVisaCode())) { throw WorkingTimeCalculator.incomplete(); }
        var confirmed = documents.findAllByUserId(profile.getId()).stream()
                .filter(value -> value.getRegisteredAt() != null && value.getObjectKey() != null)
                .collect(Collectors.toMap(StudentEnrollmentDocument::getDocumentType, Function.identity()));
        var enrollment = document(confirmed, "ENROLLMENT");
        var transcript = document(confirmed, "TRANSCRIPT").confirmedAcademic();
        var topik = document(confirmed, "TOPIK").confirmedAcademic().topikLevel();
        if (enrollment.getUniversityName() == null || enrollment.getUniversityName().isBlank()) { throw WorkingTimeCalculator.incomplete(); }
        LocalDate date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        var accreditation = universities.lookup(enrollment.getUniversityName(), date);
        var estimate = calculator.calculate(program, enrollment.getGradeLevel(), topik, transcript.averageGpa(), accreditation.accredited());
        var response = new StudentWorkingConditionsResponse(new BasicInformation(profile.getVisaCode(), program,
                enrollment.getGradeLevel(), enrollment.getUniversityName(), enrollment.getCampusName(), topik, transcript.averageGpa()),
                date, accreditation.effectiveFrom(), estimate,
                "예상 근무시간 기준입니다. 시간제취업 허가 완료를 의미하지 않으며, 실제 허용 시간은 출입국의 허가 결과를 확인해야 합니다.");
        Map<String, Object> stored = mapper.convertValue(response,
                mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        profile.updateWorkingConditions(stored);
        return response;
    }
    private StudentEnrollmentDocument document(Map<String, StudentEnrollmentDocument> documents, String kind) {
        var document = documents.get(kind);
        if (document == null) { throw WorkingTimeCalculator.incomplete(); }
        return document;
    }
}
