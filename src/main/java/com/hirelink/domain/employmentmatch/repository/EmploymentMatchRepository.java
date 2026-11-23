package com.hirelink.domain.employmentmatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.employmentmatch.entity.EmploymentMatch;
import com.hirelink.domain.member.entity.UserAccount;

public interface EmploymentMatchRepository extends JpaRepository<EmploymentMatch, Long> {

	boolean existsByEmploymentOfferId(Long employmentOfferId);

	Optional<EmploymentMatch> findByEmploymentOfferId(Long employmentOfferId);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationStudentProfileIdOrderByCompletedAtDescIdDesc(
			Long studentProfileId);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationJobPostingWorkplaceEmployerUserIdOrderByCompletedAtDescIdDesc(
			Long employerUserId);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationJobPostingIdOrderByCompletedAtDescIdDesc(Long jobPostingId);

	@Query("""
			select distinct match.employmentOffer.application.jobPosting.workplace.employerUser
			from EmploymentMatch match
			where match.employmentOffer.application.studentProfile.id = :studentProfileId
			""")
	List<UserAccount> findDistinctEmployerUsersByStudentProfileId(
			@Param("studentProfileId") Long studentProfileId);
}
