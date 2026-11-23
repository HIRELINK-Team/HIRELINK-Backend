package com.hirelink.domain.employmentmatch.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.employmentmatch.entity.EmploymentMatch;
import com.hirelink.domain.member.entity.UserAccount;

public interface EmploymentMatchRepository extends JpaRepository<EmploymentMatch, Long> {

	boolean existsByEmploymentOfferId(Long employmentOfferId);

	Optional<EmploymentMatch> findByEmploymentOfferId(Long employmentOfferId);

	@Query("select match from EmploymentMatch match where match.id = :id and match.deletedAt is null")
	Optional<EmploymentMatch> findActiveById(@Param("id") Long id);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationStudentProfileIdAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
			Long studentProfileId);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationJobPostingWorkplaceEmployerUserIdAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
			Long employerUserId);

	List<EmploymentMatch> findAllByEmploymentOfferApplicationJobPostingIdAndDeletedAtIsNullOrderByCompletedAtDescIdDesc(
			Long jobPostingId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select match from EmploymentMatch match where match.id in :ids")
	List<EmploymentMatch> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select match from EmploymentMatch match
			where match.employmentOffer.application.jobPosting.id = :jobPostingId
			and match.deletedAt is null
			""")
	List<EmploymentMatch> findAllActiveByJobPostingIdForUpdate(@Param("jobPostingId") Long jobPostingId);

	@Query("""
			select distinct match.employmentOffer.application.jobPosting.workplace.employerUser
			from EmploymentMatch match
			where match.employmentOffer.application.studentProfile.id = :studentProfileId
			and match.deletedAt is null
			""")
	List<UserAccount> findDistinctEmployerUsersByStudentProfileId(
			@Param("studentProfileId") Long studentProfileId);
}
