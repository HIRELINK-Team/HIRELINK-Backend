package com.hirelink.domain.workplace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.workplace.entity.Workplace;

public interface WorkplaceRepository extends JpaRepository<Workplace, Long> {

	@EntityGraph(attributePaths = "businessType")
	List<Workplace> findAllByEmployerUserIdOrderByIdAsc(Long employerUserId);

	@Override
	@EntityGraph(attributePaths = "businessType")
	Optional<Workplace> findById(Long id);
}
