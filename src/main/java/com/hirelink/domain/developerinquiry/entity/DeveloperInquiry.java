package com.hirelink.domain.developerinquiry.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.hirelink.domain.member.entity.MemberRole;

@Entity
@Table(name = "developer_inquiry")
public class DeveloperInquiry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "author_user_id", nullable = false)
	private Long authorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_role", nullable = false, length = 20)
	private MemberRole userRole;

	@Column(name = "reply_email", nullable = false, length = 255)
	private String replyEmail;

	@Column(name = "inquiry_type", nullable = false, length = 50)
	private String inquiryType;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "submitted_at", nullable = false, updatable = false)
	private Instant submittedAt;

	protected DeveloperInquiry() {
	}

	public DeveloperInquiry(Long authorUserId, MemberRole userRole, String replyEmail,
			String inquiryType, String title, String content, Instant submittedAt) {
		this.authorUserId = authorUserId;
		this.userRole = userRole;
		this.replyEmail = replyEmail;
		this.inquiryType = inquiryType;
		this.title = title;
		this.content = content;
		this.submittedAt = submittedAt;
	}

	public Long getId() {
		return id;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}
}
