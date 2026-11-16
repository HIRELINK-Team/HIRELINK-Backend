package com.hirelink.domain.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SocialSignupAgreement {

	@Column(name = "term_type_id", nullable = false)
	private Long termTypeId;

	@Column(name = "is_agreed", nullable = false)
	private boolean agreed;

	@Column(name = "agreed_at")
	private Instant agreedAt;

	protected SocialSignupAgreement() {
	}

	public SocialSignupAgreement(Long termTypeId) {
		this.termTypeId = termTypeId;
	}

	public void update(boolean agreed, Instant now) {
		if (agreed && !this.agreed) {
			this.agreedAt = now;
		} else if (!agreed) {
			this.agreedAt = null;
		}
		this.agreed = agreed;
	}

	public Long getTermTypeId() { return termTypeId; }
	public boolean isAgreed() { return agreed; }
	public Instant getAgreedAt() { return agreedAt; }
}
