package com.hirelink.domain.jobposting.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.hirelink.domain.workplace.entity.BusinessType;
import com.hirelink.domain.workplace.entity.Workplace;

@Entity
@Table(name = "job_posting")
public class JobPosting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workplace_id", nullable = false)
	private Workplace workplace;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "business_type_id")
	private BusinessType businessType;

	@Column(name = "other_job_name", length = 100)
	private String otherJobName;

	@Column(length = 200)
	private String title;

	@Column(name = "job_role", length = 100)
	private String jobRole;

	@Column(name = "wage_type", length = 20)
	private String wageType;

	@Column(name = "wage_amount")
	private Integer wageAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JobPostingStatus status;

	@Column(name = "recruitment_count")
	private Short recruitmentCount;

	@Column(name = "work_start_date")
	private LocalDate workStartDate;

	@Column(name = "required_korean_level", length = 30)
	private String requiredKoreanLevel;

	@Column(name = "preferred_experience", length = 30)
	private String preferredExperience;

	@Column(name = "work_time_negotiable")
	private Boolean workTimeNegotiable;

	@Column(name = "application_deadline_date")
	private LocalDate applicationDeadlineDate;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected JobPosting() {
	}

	public JobPosting(Workplace workplace, JobPostingStatus status) {
		this.workplace = workplace;
		this.status = status;
	}

	public JobPosting(Workplace workplace, JobPostingStatus status, BusinessType businessType, String title,
			String jobRole, String wageType, Integer wageAmount, Short recruitmentCount, LocalDate workStartDate,
			String requiredKoreanLevel, String preferredExperience, Boolean workTimeNegotiable, LocalDate applicationDeadlineDate) {
		this(workplace, status);
		this.businessType = businessType;
		this.title = title;
		this.jobRole = jobRole;
		this.wageType = wageType;
		this.wageAmount = wageAmount;
		this.recruitmentCount = recruitmentCount;
		this.workStartDate = workStartDate;
		this.requiredKoreanLevel = requiredKoreanLevel;
		this.preferredExperience = preferredExperience;
		this.workTimeNegotiable = workTimeNegotiable;
		this.applicationDeadlineDate = applicationDeadlineDate;
	}

	@PrePersist
	void setCreatedTimestamps() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	public void updateConditions(JobPostingStatus status, BusinessType businessType, String title, String jobRole,
			String wageType, Integer wageAmount, Short recruitmentCount, LocalDate workStartDate,
			String requiredKoreanLevel, String preferredExperience, Boolean workTimeNegotiable, LocalDate applicationDeadlineDate) {
		this.status = status;
		this.businessType = businessType;
		this.title = title;
		this.jobRole = jobRole;
		this.wageType = wageType;
		this.wageAmount = wageAmount;
		this.recruitmentCount = recruitmentCount;
		this.workStartDate = workStartDate;
		this.requiredKoreanLevel = requiredKoreanLevel;
		this.preferredExperience = preferredExperience;
		this.workTimeNegotiable = workTimeNegotiable;
		this.applicationDeadlineDate = applicationDeadlineDate;
		// 근무시간 목록만 바뀌어도 공고의 수정 시각을 갱신한다.
		this.updatedAt = Instant.now();
	}

	public void closeRecruitment() {
		status = JobPostingStatus.CLOSED;
		closedAt = Instant.now();
	}

	@PreUpdate
	void updateTimestamp() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Workplace getWorkplace() {
		return workplace;
	}

	public BusinessType getBusinessType() {
		return businessType;
	}

	public String getOtherJobName() {
		return otherJobName;
	}

	public String getTitle() {
		return title;
	}

	public String getJobRole() {
		return jobRole;
	}

	public String getWageType() {
		return wageType;
	}

	public Integer getWageAmount() {
		return wageAmount;
	}

	public JobPostingStatus getStatus() {
		return status;
	}

	public Short getRecruitmentCount() {
		return recruitmentCount;
	}

	public LocalDate getWorkStartDate() {
		return workStartDate;
	}

	public String getRequiredKoreanLevel() {
		return requiredKoreanLevel;
	}

	public String getPreferredExperience() {
		return preferredExperience;
	}

	public Boolean getWorkTimeNegotiable() {
		return workTimeNegotiable;
	}

	public LocalDate getApplicationDeadlineDate() {
		return applicationDeadlineDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}
}
