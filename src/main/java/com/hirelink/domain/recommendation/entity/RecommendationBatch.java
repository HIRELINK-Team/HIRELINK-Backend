package com.hirelink.domain.recommendation.entity;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.hirelink.domain.student.entity.StudentProfile;

@Entity
@Table(name = "recommendation_batch")
public class RecommendationBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_profile_id", nullable = false)
	private StudentProfile studentProfile;

	@Column(name = "algorithm_version", nullable = false, length = 30)
	private String algorithmVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> inputSnapshot;

	@Column(name = "invalidated_at")
	private Instant invalidatedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RecommendationBatch() {
	}

	public RecommendationBatch(StudentProfile studentProfile, String algorithmVersion,
			Map<String, Object> inputSnapshot, Instant invalidatedAt, Instant createdAt) {
		this.studentProfile = studentProfile;
		this.algorithmVersion = algorithmVersion;
		this.inputSnapshot = inputSnapshot;
		this.invalidatedAt = invalidatedAt;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public StudentProfile getStudentProfile() {
		return studentProfile;
	}

	public String getAlgorithmVersion() {
		return algorithmVersion;
	}

	public Map<String, Object> getInputSnapshot() {
		return inputSnapshot;
	}

	public Instant getInvalidatedAt() {
		return invalidatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
