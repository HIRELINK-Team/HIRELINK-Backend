package com.hirelink.domain.employmentnotification.entity;

import java.time.Instant;

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

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.member.entity.UserAccount;

@Entity
@Table(name = "employment_popup_notification", uniqueConstraints = {
		@UniqueConstraint(name = "uk_employment_popup_notification_application",
				columnNames = {"notification_type", "application_id", "recipient_user_id"}),
		@UniqueConstraint(name = "uk_employment_popup_notification_offer",
				columnNames = {"notification_type", "employment_offer_id", "recipient_user_id"})
})
public class EmploymentPopupNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_user_id", nullable = false)
	private UserAccount recipient;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_type", nullable = false, length = 40)
	private EmploymentPopupNotificationType type;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "application_id")
	private Application application;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "employment_offer_id")
	private EmploymentOffer employmentOffer;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	protected EmploymentPopupNotification() {
	}

	private EmploymentPopupNotification(UserAccount recipient, EmploymentPopupNotificationType type,
			Application application, EmploymentOffer employmentOffer, Instant createdAt) {
		this.recipient = recipient;
		this.type = type;
		this.application = application;
		this.employmentOffer = employmentOffer;
		this.createdAt = createdAt;
	}

	public static EmploymentPopupNotification forApplication(UserAccount recipient,
			EmploymentPopupNotificationType type, Application application, Instant createdAt) {
		return new EmploymentPopupNotification(recipient, type, application, null, createdAt);
	}

	public static EmploymentPopupNotification forOffer(UserAccount recipient,
			EmploymentPopupNotificationType type, EmploymentOffer employmentOffer, Instant createdAt) {
		return new EmploymentPopupNotification(recipient, type, null, employmentOffer, createdAt);
	}

	public void confirm(Instant confirmedAt) {
		if (this.confirmedAt == null) {
			this.confirmedAt = confirmedAt;
		}
	}

	public Long getId() {
		return id;
	}

	public UserAccount getRecipient() {
		return recipient;
	}

	public EmploymentPopupNotificationType getType() {
		return type;
	}

	public Long getApplicationId() {
		return application != null ? application.getId() : employmentOffer.getApplication().getId();
	}

	public Long getEmploymentOfferId() {
		return employmentOffer == null ? null : employmentOffer.getId();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}
}
