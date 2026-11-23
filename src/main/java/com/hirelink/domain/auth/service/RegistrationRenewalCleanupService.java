package com.hirelink.domain.auth.service;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;

@Service
public class RegistrationRenewalCleanupService {
    private final StudentRegistrationVerificationRepository registrations;
    public RegistrationRenewalCleanupService(StudentRegistrationVerificationRepository registrations) { this.registrations=registrations; }
    @Scheduled(fixedDelayString="${registration-verification.renewal-cleanup-delay:60000}",
            initialDelayString="${registration-verification.renewal-cleanup-initial-delay:60000}")
    public void clearExpired() { registrations.clearExpiredRenewals(Instant.now()); }
}
