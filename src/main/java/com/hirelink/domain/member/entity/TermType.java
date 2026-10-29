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

	protected TermType() {
	}
}
