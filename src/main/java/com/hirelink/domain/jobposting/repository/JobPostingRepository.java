package com.hirelink.domain.jobposting.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.entity.JobPostingStatus;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select posting from JobPosting posting where posting.id = :id")
	Optional<JobPosting> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			select posting from JobPosting posting
			join fetch posting.workplace workplace
			where workplace.employerUser.id = :employerUserId and posting.status in :statuses
			order by posting.createdAt desc, posting.id desc
			""")
	List<JobPosting> findAllByEmployerUserIdAndStatusIn(@Param("employerUserId") Long employerUserId,
			@Param("statuses") List<JobPostingStatus> statuses);
}
