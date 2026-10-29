package com.hirelink.domain.member.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserTermAgreementId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "term_type_id", nullable = false)
	private Long termTypeId;

	protected UserTermAgreementId() {
	}

	public UserTermAgreementId(Long userId, Long termTypeId) {
		this.userId = userId;
		this.termTypeId = termTypeId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof UserTermAgreementId that)) {
			return false;
		}
		return Objects.equals(userId, that.userId) && Objects.equals(termTypeId, that.termTypeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userId, termTypeId);
	}
}
