package com.hirelink.domain.employer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.employer.entity.EmployerProfile;

public interface EmployerProfileRepository extends JpaRepository<EmployerProfile, Long> {
}
