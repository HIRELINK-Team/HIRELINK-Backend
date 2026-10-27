package com.hirelink.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "auth_account", uniqueConstraints = {
		@UniqueConstraint(name = "uk_auth_account_provider_user_id", columnNames = {"provider", "provider_user_id"})
})
public class AuthAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount userAccount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	protected AuthAccount() {
	}

	public AuthAccount(UserAccount userAccount, AuthProvider provider, String providerUserId, String passwordHash) {
		this.userAccount = userAccount;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.passwordHash = passwordHash;
	}
}
