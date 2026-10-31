package com.hirelink.domain.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.recommendation.entity.Recommendation;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
}
