package com.hirelink.domain.auth.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SocialSignupAgreement {

	@Column(name = "term_type_id", nullable = false)
	private Long termTypeId;

	@Column(name = "is_agreed", nullable = false)
	private boolean agreed;

	@Column(name = "term_version", length = 64)
	private String termVersion;

	@Column(name = "agreed_at")
	private Instant agreedAt;

	protected SocialSignupAgreement() {
	}

	public SocialSignupAgreement(Long termTypeId) {
		this.termTypeId = termTypeId;
	}

	public void update(boolean agreed, Instant now, String version) {
		if (agreed && (!this.agreed || !Objects.equals(termVersion, version))) {
			this.agreedAt = now;
		} else if (!agreed) {
			this.agreedAt = null;
		}
		this.agreed = agreed;
		this.termVersion = version;
	}

	public String getTermVersion() { return termVersion; }
	public Long getTermTypeId() { return termTypeId; }
	public boolean isAgreed() { return agreed; }
	public Instant getAgreedAt() { return agreedAt; }
}
