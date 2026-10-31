package com.hirelink.domain.student.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.StudentPriority;
import com.hirelink.domain.student.entity.StudentPriorityId;

public interface StudentPriorityRepository extends JpaRepository<StudentPriority, StudentPriorityId> {

	@EntityGraph(attributePaths = "criterion")
	List<StudentPriority> findAllByIdUserIdOrderByPriorityOrderAsc(Long userId);
}
