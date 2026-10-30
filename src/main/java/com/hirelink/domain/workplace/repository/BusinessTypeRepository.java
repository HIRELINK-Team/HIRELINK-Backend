package com.hirelink.domain.workplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.workplace.entity.BusinessType;

public interface BusinessTypeRepository extends JpaRepository<BusinessType, Long> {
}
