package com.hirelink.domain.student.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentBadgeResponse;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentBadgeService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Set<String> REQUIRED_DOCUMENTS = Set.of("ENROLLMENT", "TRANSCRIPT", "TOPIK");
    private final UserAccountRepository users;
    private final StudentProfileRepository profiles;
    private final StudentRegistrationVerificationRepository registrations;
    private final StudentEnrollmentDocumentRepository documents;
    private final StudentWorkingConditionsSnapshotService snapshots;

    public StudentBadgeService(UserAccountRepository users, StudentProfileRepository profiles,
            StudentRegistrationVerificationRepository registrations, StudentEnrollmentDocumentRepository documents,
            StudentWorkingConditionsSnapshotService snapshots) {
        this.users = users; this.profiles = profiles; this.registrations = registrations; this.documents = documents;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public StudentBadgeResponse current(Long userId) {
        var user = users.findById(userId).orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
        if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
        if (user.getRole() != MemberRole.STUDENT) { throw new AccessDeniedException("유학생 본인의 배지만 조회할 수 있습니다."); }
        return status(profiles.findById(userId).orElseThrow(StudentProfileNotFoundException::new));
    }

    public StudentBadgeResponse status(StudentProfile profile) {
        var registration = registrations.findById(profile.getId()).orElse(null);
        var expiry = registration == null ? null : registration.getStayExpiresOn();
        return new StudentBadgeResponse(profile.getHirelinkBadgeIssuedAt() != null && eligible(profile),
                profile.getHirelinkBadgeIssuedAt(), expiry);
    }

    // 호출자는 회원 행을 잠그고 서류 확정/갱신과 같은 트랜잭션에서 실행한다.
    public void issueIfEligible(StudentProfile profile, boolean reissue) {
        if (eligible(profile)) {
            snapshots.refresh(profile);
            profile.issueHirelinkBadge(Instant.now(), reissue);
        }
    }

    private boolean eligible(StudentProfile profile) {
        var registration = registrations.findById(profile.getId()).orElse(null);
        if (registration == null || registration.getCardVerifiedAt() == null
                || !"D-2".equals(registration.getVisaCode())
                || registration.getStayExpiresOn().isBefore(LocalDate.now(KOREA))) { return false; }
        var confirmed = documents.findAllByUserId(profile.getId()).stream()
                .filter(doc -> doc.getRegisteredAt() != null && doc.getObjectKey() != null)
                .map(doc -> doc.getDocumentType()).collect(Collectors.toSet());
        return confirmed.containsAll(REQUIRED_DOCUMENTS);
    }
}
