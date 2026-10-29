package com.hirelink.domain.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.member.entity.TermType;

public interface TermTypeRepository extends JpaRepository<TermType, Long> {
}
