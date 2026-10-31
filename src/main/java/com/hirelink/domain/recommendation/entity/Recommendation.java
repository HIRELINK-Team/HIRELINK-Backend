package com.hirelink.domain.recommendation.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.hirelink.domain.jobposting.entity.JobPosting;

@Entity
@Table(name = "recommendation", uniqueConstraints = {
		@UniqueConstraint(name = "uk_recommendation_batch_rank", columnNames = {"batch_id", "rank"}),
		@UniqueConstraint(name = "uk_recommendation_batch_job_posting", columnNames = {"batch_id", "job_posting_id"})
})
public class Recommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "batch_id", nullable = false)
	private RecommendationBatch batch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_posting_id", nullable = false)
	private JobPosting jobPosting;

	@Column(nullable = false)
	private Short rank;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal score;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reason_json", columnDefinition = "jsonb")
	private Map<String, Object> reasonJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Recommendation() {
	}

	public Recommendation(RecommendationBatch batch, JobPosting jobPosting, Short rank, BigDecimal score,
			Map<String, Object> reasonJson, Instant createdAt) {
		this.batch = batch;
		this.jobPosting = jobPosting;
		this.rank = rank;
		this.score = score;
		this.reasonJson = reasonJson;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public RecommendationBatch getBatch() {
		return batch;
	}

	public JobPosting getJobPosting() {
		return jobPosting;
	}

	public Short getRank() {
		return rank;
	}

	public BigDecimal getScore() {
		return score;
	}

	public Map<String, Object> getReasonJson() {
		return reasonJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
