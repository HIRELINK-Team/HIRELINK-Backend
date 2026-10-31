package com.hirelink.domain.application.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.recommendation.entity.Recommendation;
import com.hirelink.domain.student.entity.StudentProfile;

@Entity
@Table(name = "application", uniqueConstraints = {
		@UniqueConstraint(name = "uk_application_recommendation", columnNames = "recommendation_id"),
		@UniqueConstraint(name = "uk_application_student_job_posting", columnNames = {"student_profile_id", "job_posting_id"})
})
public class Application {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recommendation_id", nullable = false)
	private Recommendation recommendation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_profile_id", nullable = false)
	private StudentProfile studentProfile;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_posting_id", nullable = false)
	private JobPosting jobPosting;

	@Column(name = "desired_start_date", nullable = false)
	private LocalDate desiredStartDate;

	@Column(name = "snapshot_version", nullable = false)
	private Short snapshotVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "application_snapshot", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> applicationSnapshot;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ApplicationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "end_reason", length = 50)
	private ApplicationEndReason endReason;

	@Column(name = "employer_response_deadline", nullable = false)
	private Instant employerResponseDeadline;

	@Column(name = "applied_at", nullable = false, updatable = false)
	private Instant appliedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	protected Application() {
	}

	public Application(Recommendation recommendation, StudentProfile studentProfile, JobPosting jobPosting,
			LocalDate desiredStartDate, Short snapshotVersion, Map<String, Object> applicationSnapshot,
			ApplicationStatus status, ApplicationEndReason endReason, Instant employerResponseDeadline,
			Instant appliedAt, Instant endedAt) {
		this.recommendation = recommendation;
		this.studentProfile = studentProfile;
		this.jobPosting = jobPosting;
		this.desiredStartDate = desiredStartDate;
		this.snapshotVersion = snapshotVersion;
		this.applicationSnapshot = applicationSnapshot;
		this.status = status;
		this.endReason = endReason;
		this.employerResponseDeadline = employerResponseDeadline;
		this.appliedAt = appliedAt;
		this.endedAt = endedAt;
	}

	public Long getId() {
		return id;
	}

	public Recommendation getRecommendation() {
		return recommendation;
	}

	public StudentProfile getStudentProfile() {
		return studentProfile;
	}

	public JobPosting getJobPosting() {
		return jobPosting;
	}

	public LocalDate getDesiredStartDate() {
		return desiredStartDate;
	}

	public Short getSnapshotVersion() {
		return snapshotVersion;
	}

	public Map<String, Object> getApplicationSnapshot() {
		return applicationSnapshot;
	}

	public ApplicationStatus getStatus() {
		return status;
	}

	public ApplicationEndReason getEndReason() {
		return endReason;
	}

	public Instant getEmployerResponseDeadline() {
		return employerResponseDeadline;
	}

	public Instant getAppliedAt() {
		return appliedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public boolean withdraw(Instant endedAt) {
		if (status != ApplicationStatus.WAITING_RESPONSE) {
			return false;
		}
		status = ApplicationStatus.ENDED;
		endReason = ApplicationEndReason.WITHDRAWN;
		this.endedAt = endedAt;
		return true;
	}

	public boolean reject(Instant endedAt) {
		if (status != ApplicationStatus.WAITING_RESPONSE) {
			return false;
		}
		status = ApplicationStatus.ENDED;
		endReason = ApplicationEndReason.EMPLOYER_REJECTED;
		this.endedAt = endedAt;
		return true;
	}

	public void rejectOffer(Instant endedAt) {
		status = ApplicationStatus.ENDED;
		endReason = ApplicationEndReason.STUDENT_REJECTED;
		this.endedAt = endedAt;
	}
}
