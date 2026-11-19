package com.hirelink.domain.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.hirelink.domain.student.entity.StudentRegistrationVerification;

public interface StudentRegistrationVerificationRepository extends JpaRepository<StudentRegistrationVerification, Long> {
}
