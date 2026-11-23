package com.hirelink.domain.student.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.hirelink.domain.member.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_registration_verification")
public class StudentRegistrationVerification {

	@Id
	@Column(name = "user_id")
	private Long userId;
	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount userAccount;
	@Column(nullable = false, length = 2)
	private String nationality;
	@Column(name = "visa_code", nullable = false, length = 20)
	private String visaCode;
	@Column(name = "stay_expires_on", nullable = false)
	private LocalDate stayExpiresOn;
	@Column(name = "card_verified_at", nullable = false)
	private Instant cardVerifiedAt;
	@Column(name = "document_verified_at")
	private Instant documentVerifiedAt;
	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;
	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(name = "renewal_id")
	private UUID renewalId;
	@Column(name = "renewal_expires_at")
	private Instant renewalExpiresAt;
	@Column(name = "renewal_payload", columnDefinition = "text")
	private String renewalPayload;
	@Column(name = "renewal_card_expires_on")
	private LocalDate renewalCardExpiresOn;
	@Column(name = "renewal_card_verified_at")
	private Instant renewalCardVerifiedAt;
	@Column(name = "renewal_document_expires_on")
	private LocalDate renewalDocumentExpiresOn;
	@Column(name = "renewal_document_verified_at")
	private Instant renewalDocumentVerifiedAt;

	public UUID getRenewalId() { return renewalId; }
	public Instant getRenewalExpiresAt() { return renewalExpiresAt; }
	public String getRenewalPayload() { return renewalPayload; }
	public LocalDate getRenewalCardExpiresOn() { return renewalCardExpiresOn; }
	public Instant getRenewalCardVerifiedAt() { return renewalCardVerifiedAt; }
	public LocalDate getRenewalDocumentExpiresOn() { return renewalDocumentExpiresOn; }
	public Instant getRenewalDocumentVerifiedAt() { return renewalDocumentVerifiedAt; }
	public void clearRenewal() {
		renewalId = null; renewalExpiresAt = null; renewalPayload = null;
		resetRenewalBack();
	}
	public void startRenewal(UUID id, Instant expiresAt) {
		clearRenewal(); renewalId = id; renewalExpiresAt = expiresAt;
	}
	public void saveRenewalPayload(String payload) { renewalPayload = payload; }
	public void resetRenewalBack() {
		renewalCardExpiresOn = null; renewalCardVerifiedAt = null; resetRenewalDocument();
	}
	public void resetRenewalDocument() { renewalDocumentExpiresOn = null; renewalDocumentVerifiedAt = null; }
	public void verifyRenewalCard(LocalDate expiry, Instant now, String payload) {
		renewalCardExpiresOn = expiry; renewalCardVerifiedAt = now; renewalPayload = payload;
	}
	public void verifyRenewalDocument(LocalDate expiry, Instant now) {
		renewalDocumentExpiresOn = expiry; renewalDocumentVerifiedAt = now; renewalPayload = null;
	}
	public void confirmRenewal(LocalDate expiry, Instant documentTime) {
		stayExpiresOn = expiry; cardVerifiedAt = renewalCardVerifiedAt; documentVerifiedAt = documentTime;
		clearRenewal();
	}

	protected StudentRegistrationVerification() { }

	public StudentRegistrationVerification(UserAccount userAccount, String nationality, String visaCode,
			LocalDate stayExpiresOn, Instant cardVerifiedAt, Instant documentVerifiedAt, Instant registeredAt, LocalDate birthDate) {
		this.userAccount = userAccount;
		this.nationality = nationality;
		this.visaCode = visaCode;
		this.stayExpiresOn = stayExpiresOn;
		this.cardVerifiedAt = cardVerifiedAt;
		this.documentVerifiedAt = documentVerifiedAt;
		this.registeredAt = registeredAt;
		this.birthDate = birthDate;
	}

	public String getNationality() { return nationality; }
	public LocalDate getBirthDate() { return birthDate; }
	public String getVisaCode() { return visaCode; }
	public LocalDate getStayExpiresOn() { return stayExpiresOn; }
	public Instant getCardVerifiedAt() { return cardVerifiedAt; }
	public Instant getDocumentVerifiedAt() { return documentVerifiedAt; }
}
