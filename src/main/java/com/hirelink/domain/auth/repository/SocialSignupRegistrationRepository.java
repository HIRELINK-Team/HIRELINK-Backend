package com.hirelink.domain.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.hirelink.domain.auth.entity.SocialSignupRegistration;

public interface SocialSignupRegistrationRepository extends JpaRepository<SocialSignupRegistration, String> { }
