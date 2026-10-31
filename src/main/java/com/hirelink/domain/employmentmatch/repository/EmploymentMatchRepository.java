package com.hirelink.domain.employmentmatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.employmentmatch.entity.EmploymentMatch;

public interface EmploymentMatchRepository extends JpaRepository<EmploymentMatch, Long> {

	boolean existsByEmploymentOfferId(Long employmentOfferId);

	Optional<EmploymentMatch> findByEmploymentOfferId(Long employmentOfferId);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationStudentProfileIdOrderByCompletedAtDescIdDesc(
			Long studentProfileId);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationJobPostingWorkplaceEmployerUserIdOrderByCompletedAtDescIdDesc(
			Long employerUserId);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationJobPostingIdOrderByCompletedAtDescIdDesc(Long jobPostingId);
}
