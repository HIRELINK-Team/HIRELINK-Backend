package com.hirelink.domain.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.PriorityCriterion;

public interface PriorityCriterionRepository extends JpaRepository<PriorityCriterion, Long> {
}
