package com.hirelink.domain.auth.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "social_signup_registration")
public class SocialSignupRegistration {

	@Id
	@Column(name = "token_hash", length = 64)
	private String tokenHash;
	@Column(name = "front_payload", columnDefinition = "text")
	private String frontPayload;
	@Column(name = "stay_expires_on")
	private LocalDate stayExpiresOn;
	@Column(name = "card_verified_at")
	private Instant cardVerifiedAt;

	protected SocialSignupRegistration() { }
	public SocialSignupRegistration(String tokenHash) { this.tokenHash = tokenHash; }

	public void resetFront() {
		frontPayload = null;
		resetBack();
	}

	public void resetBack() {
		stayExpiresOn = null;
		cardVerifiedAt = null;
	}

	public void saveFront(String encryptedPayload) { frontPayload = encryptedPayload; }

	public void verify(String encryptedPayload, LocalDate expiry, Instant verifiedAt) {
		frontPayload = encryptedPayload;
		stayExpiresOn = expiry;
		cardVerifiedAt = verifiedAt;
	}

	public String getFrontPayload() { return frontPayload; }
	public LocalDate getStayExpiresOn() { return stayExpiresOn; }
	public Instant getCardVerifiedAt() { return cardVerifiedAt; }
}
