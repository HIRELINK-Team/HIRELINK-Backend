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

@Entity
@Table(name = "user_account")
public class UserAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	@Column(length = 255)
	private String email;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "phone_number", length = 20)
	private String phoneNumber;

	@Column(name = "phone_verified_at")
	private Instant phoneVerifiedAt;

	@Column(name = "acquisition_channel", length = 50)
	private String acquisitionChannel;

	@Column(name = "preferred_language", length = 10)
	private String preferredLanguage = "KO";

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	@Column(name="withdrawal_requested_at") private Instant withdrawalRequestedAt;
    @Column(name="withdrawal_finalized_at") private Instant withdrawalFinalizedAt;
    @Column(name="withdrawal_retain_until") private Instant withdrawalRetainUntil;
    @Column(name="retained_data_purged_at") private Instant retainedDataPurgedAt;
    @Column(name="account_version",nullable=false) private long accountVersion;
    public Instant getWithdrawalRequestedAt() { return withdrawalRequestedAt; }
    public Instant getWithdrawalFinalizedAt() { return withdrawalFinalizedAt; }
    public long getAccountVersion() { return accountVersion; }
    public boolean canRestore(Instant now) { return withdrawalRequestedAt!=null && withdrawalFinalizedAt==null && now.isBefore(withdrawalRequestedAt.plus(java.time.Duration.ofDays(30))); }
    public void requestWithdrawal(Instant now) { withdrawnAt=now; withdrawalRequestedAt=now; accountVersion++; }
    public void restoreWithdrawal() { withdrawnAt=null; withdrawalRequestedAt=null; }
    public void finalizeWithdrawal(Instant now) {
        withdrawalFinalizedAt=now;
        withdrawalRetainUntil=now.atZone(java.time.ZoneId.of("Asia/Seoul")).plusYears(3).toInstant();
    }
    public Instant getWithdrawalRetainUntil() { return withdrawalRetainUntil; }
    public Instant getRetainedDataPurgedAt() { return retainedDataPurgedAt; }
    public boolean isRetentionExpired(Instant now) {
        return withdrawalFinalizedAt != null && withdrawalRetainUntil != null
                && retainedDataPurgedAt == null && !now.isBefore(withdrawalRetainUntil);
    }
    public void purgeRetainedInformation(Instant now) {
        if (!isRetentionExpired(now)) { throw new IllegalStateException("Retention period has not expired"); }
        name=null; email=null; emailVerifiedAt=null; phoneNumber=null; phoneVerifiedAt=null;
        acquisitionChannel=null; preferredLanguage=null; createdAt=null; retainedDataPurgedAt=now;
    }

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
		return withdrawalFinalizedAt == null ? email : null;
	}

	public MemberRole getRole() {
		return role;
	}

	public String getName() {
		return withdrawalFinalizedAt == null ? name : null;
	}

	public void updateName(String name) {
		this.name = name;
	}

	public String getPhoneNumber() {
		return withdrawalFinalizedAt == null ? phoneNumber : null;
	}

	public String getAcquisitionChannel() {
		return acquisitionChannel;
	}

	public void updateAcquisitionChannel(String acquisitionChannel) {
		this.acquisitionChannel = acquisitionChannel;
	}

	public boolean isPhoneVerified() {
		return withdrawalFinalizedAt == null && phoneNumber != null && phoneVerifiedAt != null;
	}

	public boolean isWithdrawn() {
		return withdrawnAt != null;
	}

	public void verifyPhoneNumber(String phoneNumber, Instant verifiedAt) {
		this.phoneNumber = phoneNumber;
		this.phoneVerifiedAt = verifiedAt;
	}
}
