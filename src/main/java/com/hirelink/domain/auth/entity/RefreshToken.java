package com.hirelink.domain.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.hirelink.domain.member.entity.UserAccount;

@Entity
@Table(name = "refresh_token", uniqueConstraints = {
		@UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash")
})
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount userAccount;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(UserAccount userAccount, String tokenHash, Instant expiresAt, Instant createdAt) {
		this.userAccount = userAccount;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public UserAccount getUserAccount() {
		return userAccount;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
