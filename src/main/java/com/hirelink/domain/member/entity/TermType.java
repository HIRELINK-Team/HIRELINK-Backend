package com.hirelink.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "term_type", uniqueConstraints = {
		@UniqueConstraint(name = "uk_term_type_name", columnNames = "term_type_name")
})
public class TermType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "term_type_name", nullable = false, length = 50)
	private String termTypeName;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	@Column(name = "is_social_signup", nullable = false)
	private boolean socialSignup;

	@Column(name = "is_student_registration", nullable = false)
	private boolean studentRegistration;

	@Column(name = "current_version", length = 64)
	private String currentVersion;

	public String getCurrentVersion() { return currentVersion; }

	protected TermType() {
	}

	public Long getId() { return id; }
	public String getTermTypeName() { return termTypeName; }
	public boolean isRequired() { return required; }
	public boolean isStudentRegistration() { return studentRegistration; }
}
