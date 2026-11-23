package com.hirelink.domain.student.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentWorkingConditionsResponse;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.global.exception.StudentDocumentException;

@Service
public class StudentWorkingConditionsService {
    private final StudentBadgeService badges;
    private final StudentProfileRepository profiles;
    private final UserAccountRepository users;
    private final StudentWorkingConditionsSnapshotService snapshots;
    private final Clock clock;

    public StudentWorkingConditionsService(StudentBadgeService badges, StudentProfileRepository profiles,
            UserAccountRepository users, StudentWorkingConditionsSnapshotService snapshots, Clock clock) {
        this.badges = badges; this.profiles = profiles; this.users = users;
        this.snapshots = snapshots; this.clock = clock;
    }

    @Transactional
    public StudentWorkingConditionsResponse current(Long userId) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
        if (!badges.current(userId).active()) {
            throw new StudentDocumentException(HttpStatus.FORBIDDEN, "ACTIVE_BADGE_REQUIRED", "현재 유효한 HIRELINK 인증 배지가 필요합니다.");
        }
        var profile = profiles.findById(userId).orElseThrow(WorkingTimeCalculator::incomplete);
        return snapshots.refresh(profile);
    }

    @Transactional
    public void refreshExisting(Long userId) {
        var user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null || user.getWithdrawalFinalizedAt() != null
                || (user.getWithdrawalRequestedAt() != null
                && !Instant.now(clock).isBefore(user.getWithdrawalRequestedAt().plus(Duration.ofDays(30))))) { return; }
        var profile = profiles.findById(userId).orElse(null);
        if (profile != null && badges.status(profile).active()) {
            snapshots.refresh(profile);
        }
    }
}
