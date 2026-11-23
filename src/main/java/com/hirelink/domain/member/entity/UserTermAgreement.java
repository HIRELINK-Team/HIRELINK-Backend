package com.hirelink.domain.member.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_term_agreement")
public class UserTermAgreement {

	@EmbeddedId
	private UserTermAgreementId id;

	@Column(name = "is_agreed", nullable = false)
	private boolean isAgreed;

	@Column(name = "term_version", length = 64)
	private String termVersion;

	@Column(name = "agreed_at")
	private Instant agreedAt;

	public String getTermVersion() { return termVersion; }

	protected UserTermAgreement() {
	}

	public UserTermAgreement(UserTermAgreementId id) {
		this.id = id;
	}

	public void updateAgreement(boolean agreed, Instant now, String version) {
		termVersion = version;
		if (!agreed) {
			agreedAt = null;
		} else if (!isAgreed) {
			agreedAt = now;
		}
		isAgreed = agreed;
	}

	public boolean isAgreed() {
		return isAgreed;
	}

	public Instant getAgreedAt() {
		return agreedAt;
	}
}
