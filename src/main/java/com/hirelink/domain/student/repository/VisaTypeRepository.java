package com.hirelink.domain.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.VisaType;

public interface VisaTypeRepository extends JpaRepository<VisaType, String> {
}
