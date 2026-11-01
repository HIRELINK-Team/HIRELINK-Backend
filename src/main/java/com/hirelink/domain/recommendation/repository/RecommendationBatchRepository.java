package com.hirelink.domain.recommendation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.recommendation.entity.RecommendationBatch;

public interface RecommendationBatchRepository extends JpaRepository<RecommendationBatch, Long> {

	boolean existsByStudentProfileId(Long studentProfileId);

	Optional<RecommendationBatch> findFirstByStudentProfileIdOrderByCreatedAtDescIdDesc(Long studentProfileId);
}
