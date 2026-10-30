package com.hirelink.domain.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.hirelink.domain.workplace.entity.BusinessType;

@Entity
@Table(name = "student_preferred_job")
public class StudentPreferredJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "business_type_id", nullable = false)
	private BusinessType businessType;

	@Column(name = "other_job_name", length = 100)
	private String otherJobName;

	protected StudentPreferredJob() {
	}

	public StudentPreferredJob(Long userId, BusinessType businessType, String otherJobName) {
		this.userId = userId;
		this.businessType = businessType;
		this.otherJobName = otherJobName;
	}

	public BusinessType getBusinessType() {
		return businessType;
	}

	public String getOtherJobName() {
		return otherJobName;
	}
}
