package com.hirelink.domain.student.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.hirelink.domain.student.dto.EnrollmentExtraction;

@jakarta.persistence.IdClass(StudentDocumentId.class)
@Entity
@Table(name = "student_enrollment_document")
public class StudentEnrollmentDocument {
	@Id private Long userId;
    @Id @Column(length=20) private String documentType="ENROLLMENT";
    @Column(precision=10,scale=4) private java.math.BigDecimal draftAverageGpa;
    @Column(precision=10,scale=4) private java.math.BigDecimal averageGpa;
    private Short draftTopikLevel;
    private Short topikLevel;
    private Integer draftAcademicYear;
    private Integer academicYear;
    @Column(length=10) private String draftSemester;
    @Column(length=10) private String semester;
    public StudentEnrollmentDocument(Long userId, String type) { this.userId=userId; this.documentType=type; }
    public void stageAcademic(UUID id,String key,com.hirelink.domain.student.dto.AcademicExtraction value) {
        draftId=id; draftObjectKey=key; draftAverageGpa=value.averageGpa(); draftTopikLevel=value.topikLevel();
        draftAcademicYear=value.academicYear(); draftSemester=value.semester();
    }
    public com.hirelink.domain.student.dto.AcademicExtraction academicExtraction() {
        return new com.hirelink.domain.student.dto.AcademicExtraction(draftAcademicYear,draftSemester,draftAverageGpa,draftTopikLevel);
    }
    public void confirmAcademic(Instant now) {
        objectKey=draftObjectKey; registeredAt=now; averageGpa=draftAverageGpa; topikLevel=draftTopikLevel;
        academicYear=draftAcademicYear; semester=draftSemester;
        draftId=null; draftObjectKey=null; draftAverageGpa=null; draftTopikLevel=null; draftAcademicYear=null; draftSemester=null;
    }
    public com.hirelink.domain.student.dto.AcademicExtraction confirmedAcademic() {
        return new com.hirelink.domain.student.dto.AcademicExtraction(academicYear,semester,averageGpa,topikLevel);
    }
    public String getDocumentType() { return documentType; }
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
