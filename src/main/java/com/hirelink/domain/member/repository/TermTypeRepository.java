package com.hirelink.domain.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.member.entity.TermType;

public interface TermTypeRepository extends JpaRepository<TermType, Long> {
	List<TermType> findBySocialSignupTrueOrderByIdAsc();
	Optional<TermType> findByTermTypeName(String termTypeName);
}
