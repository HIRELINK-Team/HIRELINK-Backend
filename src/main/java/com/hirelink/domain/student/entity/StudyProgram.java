package com.hirelink.domain.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_program")
public class StudyProgram {

	@Id
	private Long id;

	@Column(name = "program_name", nullable = false, length = 100)
	private String programName;

	protected StudyProgram() { }

	public Long getId() { return id; }
	public String getProgramName() { return programName; }
}
