package com.hirelink.domain.student.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.student.dto.StudentWorkingConditionsResponse;
import com.hirelink.domain.student.dto.StudentWorkingConditionsResponse.BasicInformation;
import com.hirelink.domain.student.entity.StudentEnrollmentDocument;
import com.hirelink.domain.student.entity.StudyProgramType;
import com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudyProgramRepository;
import com.hirelink.global.exception.StudentDocumentException;

@Service
public class StudentWorkingConditionsService {
    private final StudentBadgeService badges;
    private final StudentProfileRepository profiles;
    private final StudyProgramRepository programs;
    private final StudentEnrollmentDocumentRepository documents;
    private final AccreditedUniversityService universities;
    private final WorkingTimeCalculator calculator;

    public StudentWorkingConditionsService(StudentBadgeService badges, StudentProfileRepository profiles,
            StudyProgramRepository programs, StudentEnrollmentDocumentRepository documents,
            AccreditedUniversityService universities, WorkingTimeCalculator calculator) {
        this.badges = badges; this.profiles = profiles; this.programs = programs; this.documents = documents;
        this.universities = universities; this.calculator = calculator;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudentWorkingConditionsResponse current(Long userId) {
        if (!badges.current(userId).active()) {
            throw new StudentDocumentException(HttpStatus.FORBIDDEN, "ACTIVE_BADGE_REQUIRED", "현재 유효한 HIRELINK 인증 배지가 필요합니다.");
        }
        var profile = profiles.findById(userId).orElseThrow(WorkingTimeCalculator::incomplete);
        var program = profile.getStudyProgramId() == null ? null : programs.findById(profile.getStudyProgramId())
                .map(value -> StudyProgramType.fromProgramName(value.getProgramName())).orElse(null);
        if (program == null || !program.getVisaCode().equals(profile.getVisaCode())) { throw WorkingTimeCalculator.incomplete(); }
        var confirmed = documents.findAllByUserId(userId).stream()
                .filter(value -> value.getRegisteredAt() != null && value.getObjectKey() != null)
                .collect(Collectors.toMap(StudentEnrollmentDocument::getDocumentType, Function.identity()));
        var enrollment = document(confirmed, "ENROLLMENT");
        var transcript = document(confirmed, "TRANSCRIPT").confirmedAcademic();
        var topik = document(confirmed, "TOPIK").confirmedAcademic().topikLevel();
        if (enrollment.getUniversityName() == null || enrollment.getUniversityName().isBlank()) { throw WorkingTimeCalculator.incomplete(); }
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var accreditation = universities.lookup(enrollment.getUniversityName(), date);
        var estimate = calculator.calculate(program, enrollment.getGradeLevel(), topik, transcript.averageGpa(), accreditation.accredited());
        return new StudentWorkingConditionsResponse(new BasicInformation(profile.getVisaCode(), program,
                enrollment.getGradeLevel(), enrollment.getUniversityName(), enrollment.getCampusName(), topik, transcript.averageGpa()),
                date, accreditation.effectiveFrom(), estimate,
                "예상 근무시간 기준입니다. 시간제취업 허가 완료를 의미하지 않으며, 실제 허용 시간은 출입국의 허가 결과를 확인해야 합니다.");
    }
    private StudentEnrollmentDocument document(Map<String, StudentEnrollmentDocument> documents, String kind) {
        var document = documents.get(kind);
        if (document == null) { throw WorkingTimeCalculator.incomplete(); }
        return document;
    }
}
