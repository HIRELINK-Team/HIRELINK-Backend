package com.hirelink.domain.employmentoffer.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;

public interface EmploymentOfferRepository extends JpaRepository<EmploymentOffer, Long> {

	@Query("select offer.application.id from EmploymentOffer offer where offer.id = :id")
	Optional<Long> findApplicationIdById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select offer from EmploymentOffer offer where offer.id = :id")
	Optional<EmploymentOffer> findByIdForUpdate(@Param("id") Long id);

	boolean existsByApplicationId(Long applicationId);
}
