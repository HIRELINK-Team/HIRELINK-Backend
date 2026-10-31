package com.hirelink.domain.employmentmatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.employmentmatch.entity.EmploymentMatch;

public interface EmploymentMatchRepository extends JpaRepository<EmploymentMatch, Long> {
}
