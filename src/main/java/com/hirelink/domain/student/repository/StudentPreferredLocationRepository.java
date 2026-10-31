package com.hirelink.domain.student.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.StudentPreferredLocation;

public interface StudentPreferredLocationRepository extends JpaRepository<StudentPreferredLocation, Long> {

	Optional<StudentPreferredLocation> findByUserId(Long userId);
}
