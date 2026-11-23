package com.hirelink.domain.member.service;

import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
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
    private final StudentWithdrawalRetentionService retention;
    private final Clock clock;
    public StudentWithdrawalScheduler(UserAccountRepository users,StudentWithdrawalFinalizer finalizer,
            StudentWithdrawalRetentionService retention,Clock clock) {
        this.users=users; this.finalizer=finalizer; this.retention=retention; this.clock=clock;
    }
    @Scheduled(fixedDelayString="${auth.withdrawal.cleanup-delay:60000}",initialDelayString="${auth.withdrawal.cleanup-initial-delay:60000}")
    public void cleanup() {
        for(var user:users.findTop20ByWithdrawalRequestedAtBeforeAndWithdrawalFinalizedAtIsNullOrderByWithdrawalRequestedAt(Instant.now(clock).minus(Duration.ofDays(30)))) {
            try { finalizer.finalizeDue(user.getId()); }
            catch(RuntimeException ex) { log.warn("Final withdrawal cleanup failed; retry scheduled"); }
        }
        for(var user:users.findTop20ByWithdrawalRetainUntilLessThanEqualAndRetainedDataPurgedAtIsNullOrderByWithdrawalRetainUntil(Instant.now(clock))) {
            try { retention.purgeDue(user.getId()); }
            catch(RuntimeException ex) { log.warn("Withdrawal retention cleanup failed; retry scheduled"); }
        }
    }
}
