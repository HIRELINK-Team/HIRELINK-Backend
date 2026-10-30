package com.hirelink.domain.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "visa_type")
public class VisaType {

	@Id
	@Column(name = "visa_code", length = 20)
	private String visaCode;

	@Column(name = "visa_name", nullable = false, length = 100)
	private String visaName;

	protected VisaType() {
	}
}
