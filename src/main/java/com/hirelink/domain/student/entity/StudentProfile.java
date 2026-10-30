package com.hirelink.domain.student.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.hirelink.domain.member.entity.UserAccount;

@Entity
@Table(name = "student_profile")
public class StudentProfile {

	@Id
	private Long id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id", nullable = false)
	private UserAccount userAccount;

	@Column(name = "birth_date", nullable = false)
	private LocalDate birthDate;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 2)
	private String nationality;

	@Column(name = "current_region", nullable = false, length = 150)
	private String currentRegion;

	@Enumerated(EnumType.STRING)
	@Column(name = "korean_level", nullable = false, length = 30)
	private KoreanLevel koreanLevel;

	@Column(name = "visa_code", length = 20)
	private String visaCode;

	@Column(name = "university_name", length = 150)
	private String universityName;

	@Column(name = "enrollment_start_date")
	private LocalDate enrollmentStartDate;

	@Column(name = "study_program_id")
	private Long studyProgramId;

	@Column(name = "grade_level")
	private Short gradeLevel;

	@Column(name = "academic_state", length = 30)
	private String academicState;

	@Column(name = "topik_level")
	private Short topikLevel;

	@Column(name = "stay_start_date")
	private LocalDate stayStartDate;

	@Column(name = "stay_expiration_date")
	private LocalDate stayExpirationDate;

	@Column(name = "profile_image_key", length = 500)
	private String profileImageKey;

	@Column(name = "certification_prompt_dismissed_at")
	private Instant certificationPromptDismissedAt;

	@Column(name = "hirelink_badge_issued_at")
	private Instant hirelinkBadgeIssuedAt;

	protected StudentProfile() {
	}

	public StudentProfile(UserAccount userAccount, LocalDate birthDate, String nationality,
			String currentRegion, KoreanLevel koreanLevel) {
		this.userAccount = userAccount;
		this.birthDate = birthDate;
		this.nationality = nationality;
		this.currentRegion = currentRegion;
		this.koreanLevel = koreanLevel;
	}

	public Long getId() {
		return id;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public String getNationality() {
		return nationality;
	}

	public String getCurrentRegion() {
		return currentRegion;
	}

	public KoreanLevel getKoreanLevel() {
		return koreanLevel;
	}
}
