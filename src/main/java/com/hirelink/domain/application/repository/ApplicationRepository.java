package com.hirelink.domain.application.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.entity.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	@Query("select application.jobPosting.id from Application application where application.studentProfile.id = :studentProfileId")
	List<Long> findAppliedJobPostingIds(@Param("studentProfileId") Long studentProfileId);

	@Query("""
			select application.jobPosting.id, count(application)
			from Application application
			where application.status in :statuses
			group by application.jobPosting.id
			""")
	List<Object[]> countActiveReviewsByJobPosting(@Param("statuses") Collection<ApplicationStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select application from Application application where application.id = :id")
	Optional<Application> findByIdForUpdate(@Param("id") Long id);

	boolean existsByStudentProfileIdAndJobPostingId(Long studentProfileId, Long jobPostingId);

	long countByJobPostingIdAndStatusIn(Long jobPostingId, Collection<ApplicationStatus> statuses);

	long countByJobPostingIdAndStatus(Long jobPostingId, ApplicationStatus status);

	List<Application> findAllByJobPostingIdAndStatusInOrderByAppliedAtDescIdDesc(Long jobPostingId,
			Collection<ApplicationStatus> statuses);

	List<Application> findAllByJobPostingIdAndStatusOrderByAppliedAtDescIdDesc(Long jobPostingId,
			ApplicationStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select application from Application application
			where application.jobPosting.id = :jobPostingId and application.status in :statuses
			order by application.id
			""")
	List<Application> findAllByJobPostingIdAndStatusInForUpdate(@Param("jobPostingId") Long jobPostingId,
			@Param("statuses") Collection<ApplicationStatus> statuses);

	List<Application> findAllByStudentProfileIdAndStatusInOrderByAppliedAtDescIdDesc(Long studentProfileId,
			Collection<ApplicationStatus> statuses);

	List<Application> findAllByStudentProfileIdAndStatusOrderByAppliedAtDescIdDesc(Long studentProfileId,
			ApplicationStatus status);

	@Query("""
			select application.id from Application application
			where application.status = :status and application.employerResponseDeadline <= :now
			order by application.id
			""")
	List<Long> findExpiredEmployerResponseIds(@Param("status") ApplicationStatus status,
			@Param("now") Instant now);
}
