package com.hirelink.domain.employmentoffer.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.hirelink.domain.application.entity.Application;

@Entity
@Table(name = "employment_offer", uniqueConstraints = {
		@UniqueConstraint(name = "uk_employment_offer_application", columnNames = "application_id")
})
public class EmploymentOffer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false)
	private Application application;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EmploymentOfferStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "end_reason", length = 50)
	private EmploymentOfferEndReason endReason;

	@Column(name = "student_response_deadline", nullable = false)
	private Instant studentResponseDeadline;

	@Column(name = "offered_at", nullable = false, updatable = false)
	private Instant offeredAt;

	@Column(name = "responded_at")
	private Instant respondedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	protected EmploymentOffer() {
	}

	public EmploymentOffer(Application application, EmploymentOfferStatus status, EmploymentOfferEndReason endReason,
			Instant studentResponseDeadline, Instant offeredAt, Instant respondedAt, Instant endedAt) {
		this.application = application;
		this.status = status;
		this.endReason = endReason;
		this.studentResponseDeadline = studentResponseDeadline;
		this.offeredAt = offeredAt;
		this.respondedAt = respondedAt;
		this.endedAt = endedAt;
	}

	public Long getId() {
		return id;
	}

	public Application getApplication() {
		return application;
	}

	public EmploymentOfferStatus getStatus() {
		return status;
	}

	public EmploymentOfferEndReason getEndReason() {
		return endReason;
	}

	public Instant getStudentResponseDeadline() {
		return studentResponseDeadline;
	}

	public Instant getOfferedAt() {
		return offeredAt;
	}

	public Instant getRespondedAt() {
		return respondedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public void reject(Instant processedAt) {
		status = EmploymentOfferStatus.REJECTED;
		endReason = EmploymentOfferEndReason.STUDENT_REJECTED;
		respondedAt = processedAt;
		endedAt = processedAt;
	}
}
