package com.hirelink.domain.employer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.hirelink.domain.member.entity.UserAccount;

@Entity
@Table(name = "employer_profile")
public class EmployerProfile {

	@Id
	private Long id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id", nullable = false)
	private UserAccount userAccount;

	@Column(length = 50)
	private String position;

    @Column(name = "onboarding_completed_at")
    private java.time.Instant onboardingCompletedAt;

    public java.time.Instant getOnboardingCompletedAt() { return onboardingCompletedAt; }
    public void completeOnboarding(java.time.Instant now) { onboardingCompletedAt = now; }

	protected EmployerProfile() {
	}

	public EmployerProfile(UserAccount userAccount) {
		this.userAccount = userAccount;
	}

	public Long getId() {
		return id;
	}

	public String getPosition() {
		return position;
	}

	public void updatePosition(String position) {
		this.position = position;
	}
}
