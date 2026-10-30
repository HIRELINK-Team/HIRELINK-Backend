package com.hirelink.domain.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "priority_criterion", uniqueConstraints = {
		@UniqueConstraint(name = "uk_priority_criterion_name", columnNames = "criterion_name")
})
public class PriorityCriterion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "criterion_name", nullable = false, length = 50)
	private String criterionName;

	protected PriorityCriterion() {
	}

	public Long getId() {
		return id;
	}

	public String getCriterionName() {
		return criterionName;
	}
}
