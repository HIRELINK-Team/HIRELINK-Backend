package com.hirelink.domain.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "student_career")
public class StudentCareer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "career_name", nullable = false, length = 150)
	private String careerName;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "start_month", nullable = false, length = 7)
	private String startMonth;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "end_month", nullable = false, length = 7)
	private String endMonth;

	@Column(columnDefinition = "text")
	private String description;

	protected StudentCareer() {
	}

	public Long getId() {
		return id;
	}

	public String getCareerName() {
		return careerName;
	}

	public String getStartMonth() {
		return startMonth;
	}

	public String getEndMonth() {
		return endMonth;
	}

	public String getDescription() {
		return description;
	}
}
