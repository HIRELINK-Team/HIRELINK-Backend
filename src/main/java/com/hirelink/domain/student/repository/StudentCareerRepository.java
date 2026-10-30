package com.hirelink.domain.student.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.StudentCareer;

public interface StudentCareerRepository extends JpaRepository<StudentCareer, Long> {

	List<StudentCareer> findAllByUserIdOrderByStartMonthDescIdDesc(Long userId);
}
