package com.hirelink.domain.application.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.entity.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select application from Application application where application.id = :id")
	Optional<Application> findByIdForUpdate(@Param("id") Long id);

	boolean existsByStudentProfileIdAndJobPostingId(Long studentProfileId, Long jobPostingId);

	long countByJobPostingIdAndStatusIn(Long jobPostingId, Collection<ApplicationStatus> statuses);
}
