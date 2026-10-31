package com.hirelink.domain.student.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.StudentPreferredWorkTime;

public interface StudentPreferredWorkTimeRepository extends JpaRepository<StudentPreferredWorkTime, Long> {

	List<StudentPreferredWorkTime> findAllByUserId(Long userId);

	void deleteAllByUserId(Long userId);
}
