package com.hirelink.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.auth.entity.PhoneVerification;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {
	Optional<PhoneVerification> findByUserId(Long userId);
}
