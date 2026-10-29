package com.hirelink.domain.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "phone_verification_send_attempt")
public class PhoneVerificationSendAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "phone_number", nullable = false, length = 20)
	private String phoneNumber;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	protected PhoneVerificationSendAttempt() {
	}

	public PhoneVerificationSendAttempt(Long userId, String phoneNumber, Instant requestedAt) {
		this.userId = userId;
		this.phoneNumber = phoneNumber;
		this.requestedAt = requestedAt;
	}
}
