package com.hirelink.domain.member.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_account", uniqueConstraints = {
		@UniqueConstraint(name = "uk_user_account_email", columnNames = "email"),
		@UniqueConstraint(name = "uk_user_account_phone_number", columnNames = "phone_number")
})
public class UserAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "phone_number", length = 20)
	private String phoneNumber;

	@Column(name = "phone_verified_at")
	private Instant phoneVerifiedAt;

	@Column(name = "acquisition_channel", length = 50)
	private String acquisitionChannel;

	@Column(name = "preferred_language", nullable = false, length = 10)
	private String preferredLanguage = "KO";

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	protected UserAccount() {
	}

	public UserAccount(String email, MemberRole role) {
		this.email = email;
		this.role = role;
	}

	@PrePersist
	void setCreatedTimestamps() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void updateTimestamp() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public MemberRole getRole() {
		return role;
	}

	public String getName() {
		return name;
	}

	public void updateName(String name) {
		this.name = name;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getAcquisitionChannel() {
		return acquisitionChannel;
	}

	public void updateAcquisitionChannel(String acquisitionChannel) {
		this.acquisitionChannel = acquisitionChannel;
	}

	public boolean isPhoneVerified() {
		return phoneNumber != null && phoneVerifiedAt != null;
	}

	public boolean isWithdrawn() {
		return withdrawnAt != null;
	}

	public void verifyPhoneNumber(String phoneNumber, Instant verifiedAt) {
		this.phoneNumber = phoneNumber;
		this.phoneVerifiedAt = verifiedAt;
	}
}
