package com.hirelink.domain.student.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.student.entity.StudentRegistrationVerification;

public interface StudentRegistrationVerificationRepository extends JpaRepository<StudentRegistrationVerification, Long> {
    @Modifying
    @Transactional
    @Query("""
        update StudentRegistrationVerification r set r.renewalId=null, r.renewalExpiresAt=null,
        r.renewalPayload=null, r.renewalCardExpiresOn=null, r.renewalCardVerifiedAt=null,
        r.renewalDocumentExpiresOn=null, r.renewalDocumentVerifiedAt=null
        where r.renewalExpiresAt <= :now
        """)
    int clearExpiredRenewals(@Param("now") Instant now);
}
