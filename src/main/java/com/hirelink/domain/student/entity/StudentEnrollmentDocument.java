package com.hirelink.domain.student.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.hirelink.domain.student.dto.EnrollmentExtraction;

@Entity
@Table(name = "student_enrollment_document")
public class StudentEnrollmentDocument {
	@Id private Long userId;
	private UUID draftId;
	@Column(length = 500) private String draftObjectKey;
	@Column(length = 150) private String draftUniversityName;
	@Column(length = 150) private String draftCampusName;
	private Integer draftGradeLevel;
	private Integer draftSemesterCount;
	@Column(length = 500) private String objectKey;
	@Column(length = 150) private String universityName;
	@Column(length = 150) private String campusName;
	private Short gradeLevel;
	private Instant registeredAt;

	protected StudentEnrollmentDocument() { }
	public StudentEnrollmentDocument(Long userId) { this.userId = userId; }
	public void stage(UUID id, String key, EnrollmentExtraction result) {
		draftId = id; draftObjectKey = key; draftUniversityName = result.universityName(); draftCampusName = result.campusName();
		draftGradeLevel = result.gradeLevel(); draftSemesterCount = result.semesterCount();
	}
	public EnrollmentExtraction extraction() { return new EnrollmentExtraction(draftUniversityName, draftCampusName, draftGradeLevel, draftSemesterCount); }
	public void confirm(short grade, Instant now) {
		objectKey = draftObjectKey; universityName = draftUniversityName; campusName = draftCampusName; gradeLevel = grade; registeredAt = now;
		draftId = null; draftObjectKey = null; draftUniversityName = null; draftCampusName = null; draftGradeLevel = null; draftSemesterCount = null;
	}
	public Long getUserId() { return userId; }
	public UUID getDraftId() { return draftId; }
	public String getDraftObjectKey() { return draftObjectKey; }
	public String getObjectKey() { return objectKey; }
	public String getUniversityName() { return universityName; }
	public String getCampusName() { return campusName; }
	public Short getGradeLevel() { return gradeLevel; }
	public Instant getRegisteredAt() { return registeredAt; }
}
