package com.hirelink.domain.employmentoffer.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

public interface EmploymentOfferRepository extends JpaRepository<EmploymentOffer, Long> {

	@Query("select offer.application.id from EmploymentOffer offer where offer.id = :id")
	Optional<Long> findApplicationIdById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select offer from EmploymentOffer offer where offer.id = :id")
	Optional<EmploymentOffer> findByIdForUpdate(@Param("id") Long id);

	boolean existsByApplicationId(Long applicationId);

	Optional<EmploymentOffer> findByApplicationId(Long applicationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select offer from EmploymentOffer offer where offer.application.id = :applicationId")
	Optional<EmploymentOffer> findByApplicationIdForUpdate(@Param("applicationId") Long applicationId);

	@Query("""
			select offer.application.id from EmploymentOffer offer
			where offer.status = :status and offer.application.status = :applicationStatus
			and offer.studentResponseDeadline <= :now
			order by offer.application.id
			""")
	List<Long> findExpiredStudentResponseApplicationIds(@Param("status") EmploymentOfferStatus status,
			@Param("applicationStatus") ApplicationStatus applicationStatus,
			@Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select offer from EmploymentOffer offer
			where offer.application.id in :applicationIds and offer.status = :status
			order by offer.id
			""")
	List<EmploymentOffer> findAllByApplicationIdInAndStatusForUpdate(
			@Param("applicationIds") Collection<Long> applicationIds,
			@Param("status") EmploymentOfferStatus status);
}
