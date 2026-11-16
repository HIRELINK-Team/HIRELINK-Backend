package com.hirelink.domain.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "phone_verification")
public class PhoneVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", unique = true)
	private Long userId;

	@Column(name = "signup_token_hash", unique = true, length = 64)
	private String signupTokenHash;

	@Column(name = "phone_number", nullable = false, length = 20)
	private String phoneNumber;

	@Column(name = "code_hash", nullable = false, length = 255)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	protected PhoneVerification() {
	}

	public PhoneVerification(Long userId, String phoneNumber, String codeHash, Instant expiresAt) {
		this.userId = userId;
		this.phoneNumber = phoneNumber;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
	}

	public boolean isActive(Instant now) {
		return expiresAt.isAfter(now);
	}

	public static PhoneVerification forSignup(String tokenHash, String phoneNumber, String codeHash, Instant expiresAt) {
		PhoneVerification verification = new PhoneVerification(null, phoneNumber, codeHash, expiresAt);
		verification.signupTokenHash = tokenHash;
		return verification;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public void recordFailure() {
		failedAttempts++;
	}

	public boolean isBlocked(Instant now) {
		return isActive(now) && failedAttempts >= 5;
	}

	public void replaceCode(String phoneNumber, String codeHash, Instant now, Instant expiresAt) {
		// 재전송이나 번호 변경으로 유효한 인증의 실패 횟수를 초기화하지 않는다.
		if (!isActive(now)) {
			failedAttempts = 0;
		}
		this.phoneNumber = phoneNumber;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
	}
}
