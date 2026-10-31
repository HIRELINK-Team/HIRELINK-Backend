package com.hirelink.domain.jobposting.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.jobposting.entity.JobPostingWorkTime;

public interface JobPostingWorkTimeRepository extends JpaRepository<JobPostingWorkTime, Long> {

	List<JobPostingWorkTime> findAllByJobPostingIdIn(List<Long> jobPostingIds);
}
