package com.hirelink.domain.member.service;

import java.time.Instant;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.hirelink.domain.member.repository.UserAccountRepository;

@Component
@ConditionalOnProperty(name="auth.withdrawal.enabled",havingValue="true")
public class StudentWithdrawalScheduler {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(StudentWithdrawalScheduler.class);
    private final UserAccountRepository users;
    private final StudentWithdrawalFinalizer finalizer;
    public StudentWithdrawalScheduler(UserAccountRepository users,StudentWithdrawalFinalizer finalizer) { this.users=users; this.finalizer=finalizer; }
    @Scheduled(fixedDelayString="${auth.withdrawal.cleanup-delay:60000}",initialDelayString="${auth.withdrawal.cleanup-initial-delay:60000}")
    public void cleanup() {
        for(var user:users.findTop20ByWithdrawalRequestedAtBeforeAndWithdrawalFinalizedAtIsNullOrderByWithdrawalRequestedAt(Instant.now().minus(Duration.ofDays(30)))) {
            try { finalizer.finalizeDue(user.getId()); }
            catch(RuntimeException ex) { log.warn("Final withdrawal cleanup failed; retry scheduled"); }
        }
    }
}
