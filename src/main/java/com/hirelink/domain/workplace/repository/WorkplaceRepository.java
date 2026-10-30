package com.hirelink.domain.workplace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.workplace.entity.Workplace;

public interface WorkplaceRepository extends JpaRepository<Workplace, Long> {

	@EntityGraph(attributePaths = "businessType")
	List<Workplace> findAllByEmployerUserIdOrderByIdAsc(Long employerUserId);
}
