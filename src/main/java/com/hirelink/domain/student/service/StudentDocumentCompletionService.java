package com.hirelink.domain.student.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentDocumentCompletionResponse;
import com.hirelink.domain.student.entity.StudentEnrollmentDocument;
import com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentDocumentCompletionService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final UserAccountRepository users;
    private final StudentProfileRepository profiles;
    private final StudentRegistrationVerificationRepository registrations;
    private final StudentEnrollmentDocumentRepository documents;

    public StudentDocumentCompletionService(UserAccountRepository users, StudentProfileRepository profiles,
            StudentRegistrationVerificationRepository registrations, StudentEnrollmentDocumentRepository documents) {
        this.users = users; this.profiles = profiles; this.registrations = registrations; this.documents = documents;
    }

    // 갱신/서류 확정과 동시에 조회해도 하나의 DB 스냅샷에서 네 항목을 계산한다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudentDocumentCompletionResponse current(Long userId) {
        var user = users.findById(userId).orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
        if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
        if (user.getRole() != MemberRole.STUDENT) { throw new AccessDeniedException("유학생 본인의 서류 완성률만 조회할 수 있습니다."); }
        if (!profiles.existsById(userId)) { throw new StudentProfileNotFoundException(); }

        LocalDate today = LocalDate.now(KOREA);
        var registration = registrations.findById(userId).orElse(null);
        boolean registrationIncluded = registration != null && registration.getCardVerifiedAt() != null
                && !registration.getStayExpiresOn().isBefore(today);
        var confirmed = documents.findAllByUserId(userId).stream()
                .filter(document -> document.getRegisteredAt() != null && document.getObjectKey() != null)
                .map(StudentEnrollmentDocument::getDocumentType).collect(Collectors.toSet());
        boolean enrollmentIncluded = confirmed.contains("ENROLLMENT");
        boolean transcriptIncluded = confirmed.contains("TRANSCRIPT");
        boolean topikIncluded = confirmed.contains("TOPIK");
        int count = (registrationIncluded ? 1 : 0) + (enrollmentIncluded ? 1 : 0)
                + (transcriptIncluded ? 1 : 0) + (topikIncluded ? 1 : 0);
        return new StudentDocumentCompletionResponse(count * 25, registrationIncluded, enrollmentIncluded,
                transcriptIncluded, topikIncluded);
    }
}
