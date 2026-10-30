package com.hirelink.domain.student.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.student.entity.StudentPreferredJob;

public interface StudentPreferredJobRepository extends JpaRepository<StudentPreferredJob, Long> {

	@EntityGraph(attributePaths = "businessType")
	@Query("select job from StudentPreferredJob job where job.userId = :userId order by job.businessType.id, job.id")
	List<StudentPreferredJob> findAllByUserId(@Param("userId") Long userId);
}
