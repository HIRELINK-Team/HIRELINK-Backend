package com.hirelink.global.sms;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sms_send_history", uniqueConstraints = {
		@UniqueConstraint(name = "uk_sms_send_history_idempotency_key", columnNames = "idempotency_key")
})
public class SmsSendHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	@Column(nullable = false, length = 20)
	private String recipient;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private SmsSendStatus status;

	@Column(name = "provider_message_id", length = 100)
	private String providerMessageId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "failed_at")
	private Instant failedAt;

	protected SmsSendHistory() {
	}

	public SmsSendHistory(String recipient, String idempotencyKey, Instant createdAt) {
		this.recipient = recipient;
		this.idempotencyKey = idempotencyKey;
		this.createdAt = createdAt;
		this.status = SmsSendStatus.PENDING;
	}

	public void markSent(String providerMessageId, Instant sentAt) {
		if (status != SmsSendStatus.PENDING) {
			return;
		}
		this.status = SmsSendStatus.SENT;
		this.providerMessageId = providerMessageId;
		this.sentAt = sentAt;
	}

	public void markFailed(Instant failedAt) {
		if (status != SmsSendStatus.PENDING) {
			return;
		}
		this.status = SmsSendStatus.FAILED;
		this.failedAt = failedAt;
	}

	public Long getId() { return id; }
	public String getRecipient() { return recipient; }
	public SmsSendStatus getStatus() { return status; }
}
