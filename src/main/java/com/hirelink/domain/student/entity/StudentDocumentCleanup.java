package com.hirelink.domain.student.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_document_cleanup")
public class StudentDocumentCleanup {
	@Id @Column(length = 500) private String objectKey;
	private Instant availableAt;
	protected StudentDocumentCleanup() { }
	public StudentDocumentCleanup(String objectKey, Instant availableAt) { this.objectKey = objectKey; this.availableAt = availableAt; }
	public String getObjectKey() { return objectKey; }
	public void retryAfter(Instant time) { availableAt = time; }
}
