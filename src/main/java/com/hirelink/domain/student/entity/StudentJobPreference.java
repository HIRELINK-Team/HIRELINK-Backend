package com.hirelink.domain.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_job_preference")
public class StudentJobPreference {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "minimum_hourly_wage")
	private Integer minimumHourlyWage;

	@Column(name = "business_type_flexible", nullable = false)
	private boolean businessTypeFlexible;

	protected StudentJobPreference() {
	}

	public Integer getMinimumHourlyWage() {
		return minimumHourlyWage;
	}

	public boolean isBusinessTypeFlexible() {
		return businessTypeFlexible;
	}
}
