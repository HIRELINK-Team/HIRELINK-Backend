package com.hirelink.domain.auth.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.hirelink.domain.member.entity.MemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "social_auth_session")
public class SocialAuthSession {

	@Id
	@Column(name = "token_hash", length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	@Column(length = 255)
	private String email;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@ElementCollection
	@CollectionTable(name = "social_auth_session_term_agreement", joinColumns = @JoinColumn(name = "token_hash"))
	private List<SocialSignupAgreement> agreements = new ArrayList<>();

	protected SocialAuthSession() {
	}

	public SocialAuthSession(String tokenHash, AuthProvider provider, String providerUserId,
			MemberRole role, String email, Instant createdAt, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.role = role;
		this.email = email;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public AuthProvider getProvider() { return provider; }
	public String getTokenHash() { return tokenHash; }
	public String getProviderUserId() { return providerUserId; }
	public MemberRole getRole() { return role; }
	public String getEmail() { return email; }
	public Instant getExpiresAt() { return expiresAt; }
	public List<SocialSignupAgreement> getAgreements() { return List.copyOf(agreements); }

	public void saveAgreement(Long termTypeId, boolean agreed, Instant now) {
		SocialSignupAgreement agreement = agreements.stream()
				.filter(item -> item.getTermTypeId().equals(termTypeId))
				.findFirst().orElseGet(() -> {
					SocialSignupAgreement item = new SocialSignupAgreement(termTypeId);
					agreements.add(item);
					return item;
				});
		agreement.update(agreed, now);
	}
}
