package com.hirelink.domain.student.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class StudentPriorityId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "criterion_id", nullable = false)
	private Long criterionId;

	protected StudentPriorityId() {
	}

	public StudentPriorityId(Long userId, Long criterionId) {
		this.userId = userId;
		this.criterionId = criterionId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof StudentPriorityId that)) {
			return false;
		}
		return Objects.equals(userId, that.userId) && Objects.equals(criterionId, that.criterionId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userId, criterionId);
	}
}
