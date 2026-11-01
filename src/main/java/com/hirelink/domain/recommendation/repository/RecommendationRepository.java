package com.hirelink.domain.recommendation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.recommendation.entity.Recommendation;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

	@EntityGraph(attributePaths = {"jobPosting", "jobPosting.workplace"})
	List<Recommendation> findAllByBatchIdOrderByRankAsc(Long batchId);
}
