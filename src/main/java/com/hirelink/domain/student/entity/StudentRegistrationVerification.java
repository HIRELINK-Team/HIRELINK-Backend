package com.hirelink.domain.student.entity;

import java.time.Instant;
import java.time.LocalDate;

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

	protected StudentRegistrationVerification() { }

	public StudentRegistrationVerification(UserAccount userAccount, String nationality, String visaCode,
			LocalDate stayExpiresOn, Instant cardVerifiedAt, Instant documentVerifiedAt, Instant registeredAt) {
		this.userAccount = userAccount;
		this.nationality = nationality;
		this.visaCode = visaCode;
		this.stayExpiresOn = stayExpiresOn;
		this.cardVerifiedAt = cardVerifiedAt;
		this.documentVerifiedAt = documentVerifiedAt;
		this.registeredAt = registeredAt;
	}

	public String getNationality() { return nationality; }
	public String getVisaCode() { return visaCode; }
	public LocalDate getStayExpiresOn() { return stayExpiresOn; }
	public Instant getCardVerifiedAt() { return cardVerifiedAt; }
	public Instant getDocumentVerifiedAt() { return documentVerifiedAt; }
}
