package com.hirelink.domain.member.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.StudentWithdrawalRetentionRepository;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;

@Service
public class StudentWithdrawalRetentionService {
    private final UserAccountRepository users;
    private final StudentProfileRepository profiles;
    private final StudentWithdrawalRetentionRepository retention;
    private final Clock clock;

    public StudentWithdrawalRetentionService(UserAccountRepository users, StudentProfileRepository profiles,
            StudentWithdrawalRetentionRepository retention, Clock clock) {
        this.users = users; this.profiles = profiles; this.retention = retention; this.clock = clock;
    }

    @Transactional
    public void purgeDue(Long userId) {
        var user = users.findByIdForUpdate(userId).orElse(null);
        Instant now = Instant.now(clock);
        if (user == null || user.getRole() != MemberRole.STUDENT || !user.isRetentionExpired(now)) { return; }
        retention.deleteRetainedRecords(userId);
        // 채용 관계의 FK를 유지하기 위해 식별 행은 남기고 보존 정보만 제거한다.
        profiles.findById(userId).ifPresent(profile -> profile.purgeRetainedInformation(now));
        user.purgeRetainedInformation(now);
    }
}
