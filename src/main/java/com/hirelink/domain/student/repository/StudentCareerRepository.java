package com.hirelink.domain.student.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.student.entity.StudentCareer;

public interface StudentCareerRepository extends JpaRepository<StudentCareer, Long> {

	List<StudentCareer> findAllByUserIdOrderByStartMonthDescIdDesc(Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select career from StudentCareer career where career.id = :id")
	Optional<StudentCareer> findByIdForUpdate(@Param("id") Long id);
}
