package com.hirelink.domain.workplace.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.workplace.entity.Workplace;

public interface WorkplaceRepository extends JpaRepository<Workplace, Long> {

	boolean existsByBusinessRegistrationNumberIn(List<String> numbers);

	@EntityGraph(attributePaths = "businessType")
	List<Workplace> findAllByEmployerUserIdOrderByIdAsc(Long employerUserId);

	@Override
	@EntityGraph(attributePaths = "businessType")
	Optional<Workplace> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select workplace from Workplace workplace where workplace.id = :id")
	Optional<Workplace> findByIdForUpdate(@Param("id") Long id);
}
