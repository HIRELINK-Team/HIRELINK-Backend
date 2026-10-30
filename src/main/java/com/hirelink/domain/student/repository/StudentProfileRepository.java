package com.hirelink.domain.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
}
