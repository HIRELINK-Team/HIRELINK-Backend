package com.hirelink.domain.employmentmatch.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;

@Entity
@Table(name = "employment_match", uniqueConstraints = {
		@UniqueConstraint(name = "uk_employment_match_offer", columnNames = "employment_offer_id")
})
public class EmploymentMatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employment_offer_id", nullable = false)
	private EmploymentOffer employmentOffer;

	@Column(name = "completed_at", nullable = false, updatable = false)
	private Instant completedAt;

	protected EmploymentMatch() {
	}

	public EmploymentMatch(EmploymentOffer employmentOffer, Instant completedAt) {
		this.employmentOffer = employmentOffer;
		this.completedAt = completedAt;
	}

	public Long getId() {
		return id;
	}

	public EmploymentOffer getEmploymentOffer() {
		return employmentOffer;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
