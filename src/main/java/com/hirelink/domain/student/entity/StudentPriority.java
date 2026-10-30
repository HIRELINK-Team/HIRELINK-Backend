package com.hirelink.domain.student.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "student_priority", uniqueConstraints = {
		@UniqueConstraint(name = "uk_student_priority_order", columnNames = {"user_id", "priority_order"})
})
public class StudentPriority {

	@EmbeddedId
	private StudentPriorityId id;

	@MapsId("criterionId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "criterion_id", nullable = false)
	private PriorityCriterion criterion;

	@Column(name = "priority_order", nullable = false)
	private Short priorityOrder;

	@Column(nullable = false, precision = 6, scale = 3)
	private BigDecimal weight;

	protected StudentPriority() {
	}

	public PriorityCriterion getCriterion() {
		return criterion;
	}

	public Short getPriorityOrder() {
		return priorityOrder;
	}
}
