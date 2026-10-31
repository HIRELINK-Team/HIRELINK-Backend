package com.hirelink.domain.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.application.dto.ApplicationCreateRequest;
import com.hirelink.domain.application.dto.ApplicationCreateResponse;
import com.hirelink.domain.application.dto.ApplicationRejectResponse;
import com.hirelink.domain.application.dto.ApplicationWithdrawResponse;
import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.entity.JobPostingStatus;
import com.hirelink.domain.jobposting.entity.JobPostingWorkTime;
import com.hirelink.domain.jobposting.repository.JobPostingRepository;
import com.hirelink.domain.jobposting.repository.JobPostingWorkTimeRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.recommendation.entity.Recommendation;
import com.hirelink.domain.recommendation.repository.RecommendationRepository;
import com.hirelink.domain.student.entity.StudentPreferredWorkTime;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.repository.StudentCareerRepository;
import com.hirelink.domain.student.repository.StudentPreferredWorkTimeRepository;
import com.hirelink.global.exception.ApplicationCapacityExceededException;
import com.hirelink.global.exception.ApplicationNotFoundException;
import com.hirelink.global.exception.ApplicationStateConflictException;
import com.hirelink.global.exception.DuplicateApplicationException;
import com.hirelink.global.exception.InvalidApplicationException;
import com.hirelink.global.exception.JobPostingNotFoundException;
import com.hirelink.global.exception.JobPostingStateConflictException;
import com.hirelink.global.exception.RecommendationNotFoundException;
import com.hirelink.global.exception.RecommendationStateConflictException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class ApplicationService {
	private static final int MAX_ACTIVE_APPLICATIONS = 5;
	private static final short SNAPSHOT_VERSION = 1;
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<ApplicationStatus> ACTIVE_APPLICATION_STATUSES = List.of(
			ApplicationStatus.WAITING_RESPONSE);

	private final UserAccountRepository userAccountRepository;
	private final ApplicationRepository applicationRepository;
	private final RecommendationRepository recommendationRepository;
	private final JobPostingRepository jobPostingRepository;
	private final JobPostingWorkTimeRepository jobPostingWorkTimeRepository;
	private final StudentCareerRepository studentCareerRepository;
	private final StudentPreferredWorkTimeRepository studentPreferredWorkTimeRepository;

	public ApplicationService(UserAccountRepository userAccountRepository,
			ApplicationRepository applicationRepository, RecommendationRepository recommendationRepository,
			JobPostingRepository jobPostingRepository, JobPostingWorkTimeRepository jobPostingWorkTimeRepository,
			StudentCareerRepository studentCareerRepository,
			StudentPreferredWorkTimeRepository studentPreferredWorkTimeRepository) {
		this.userAccountRepository = userAccountRepository;
		this.applicationRepository = applicationRepository;
		this.recommendationRepository = recommendationRepository;
		this.jobPostingRepository = jobPostingRepository;
		this.jobPostingWorkTimeRepository = jobPostingWorkTimeRepository;
		this.studentCareerRepository = studentCareerRepository;
		this.studentPreferredWorkTimeRepository = studentPreferredWorkTimeRepository;
	}

	@Transactional
	public ApplicationCreateResponse createApplication(Long userId, ApplicationCreateRequest request) {
		UserAccount user = getStudentUser(userId);
		Recommendation recommendation = recommendationRepository.findById(request.recommendationId())
				.orElseThrow(RecommendationNotFoundException::new);
		StudentProfile studentProfile = recommendation.getBatch().getStudentProfile();
		if (!studentProfile.getId().equals(userId)) {
			throw new AccessDeniedException("본인에게 생성된 추천으로만 지원할 수 있습니다.");
		}
		if (recommendation.getBatch().getInvalidatedAt() != null) {
			throw new RecommendationStateConflictException();
		}

		Long jobPostingId = recommendation.getJobPosting().getId();
		JobPosting jobPosting = jobPostingRepository.findByIdForUpdate(jobPostingId)
				.orElseThrow(JobPostingNotFoundException::new);
		LocalDate today = LocalDate.now(KOREA_ZONE);
		validateApplicationDates(request.desiredStartDate(), jobPosting, today);
		if (jobPosting.getStatus() != JobPostingStatus.RECRUITING
				|| jobPosting.getApplicationDeadlineDate() != null
				&& jobPosting.getApplicationDeadlineDate().isBefore(today)) {
			throw new JobPostingStateConflictException("현재 모집 중인 채용공고에만 지원할 수 있습니다.");
		}
		if (applicationRepository.existsByStudentProfileIdAndJobPostingId(userId, jobPostingId)) {
			throw new DuplicateApplicationException();
		}
		long activeApplicationCount = applicationRepository.countByJobPostingIdAndStatusIn(
				jobPostingId, ACTIVE_APPLICATION_STATUSES);
		if (activeApplicationCount >= MAX_ACTIVE_APPLICATIONS) {
			throw new ApplicationCapacityExceededException();
		}

		Instant appliedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Map<String, Object> snapshot = createApplicationSnapshot(user, studentProfile, recommendation, jobPosting);
		Application application = new Application(recommendation, studentProfile, jobPosting,
				request.desiredStartDate(), SNAPSHOT_VERSION, snapshot, ApplicationStatus.WAITING_RESPONSE,
				null, appliedAt.plus(24, ChronoUnit.HOURS), appliedAt, null);
		applicationRepository.saveAndFlush(application);
		return new ApplicationCreateResponse(application.getId(), recommendation.getId(), jobPosting.getId(),
				application.getDesiredStartDate(), application.getStatus(), application.getEmployerResponseDeadline(),
				application.getAppliedAt());
	}

	@Transactional
	public ApplicationWithdrawResponse withdrawApplication(Long userId, Long applicationId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 지원을 취소할 수 있습니다.");
		}

		Application application = applicationRepository.findByIdForUpdate(applicationId)
				.orElseThrow(ApplicationNotFoundException::new);
		if (!application.getStudentProfile().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 지원만 취소할 수 있습니다.");
		}

		Instant endedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		if (!application.withdraw(endedAt)) {
			throw new ApplicationStateConflictException();
		}
		applicationRepository.flush();
		return new ApplicationWithdrawResponse(application.getId(), application.getStatus(),
				application.getEndReason(), application.getEndedAt());
	}

	@Transactional
	public ApplicationRejectResponse rejectApplication(Long userId, Long applicationId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 지원을 거절할 수 있습니다.");
		}

		Application application = applicationRepository.findByIdForUpdate(applicationId)
				.orElseThrow(ApplicationNotFoundException::new);
		if (!application.getJobPosting().getWorkplace().getEmployerUser().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 채용공고에 접수된 지원만 거절할 수 있습니다.");
		}

		Instant endedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		if (!application.reject(endedAt)) {
			throw new ApplicationStateConflictException();
		}
		applicationRepository.flush();
		return new ApplicationRejectResponse(application.getId(), application.getStatus(),
				application.getEndReason(), application.getEndedAt());
	}

	private UserAccount getStudentUser(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 지원할 수 있습니다.");
		}
		return user;
	}

	private void validateApplicationDates(LocalDate desiredStartDate, JobPosting jobPosting, LocalDate today) {
		if (desiredStartDate.isBefore(today)) {
			throw new InvalidApplicationException("희망 근무 시작일은 오늘 이후여야 합니다.");
		}
		if (jobPosting.getWorkStartDate() != null && desiredStartDate.isBefore(jobPosting.getWorkStartDate())) {
			throw new InvalidApplicationException("희망 근무 시작일은 공고의 근무 시작일보다 빠를 수 없습니다.");
		}
	}

	private Map<String, Object> createApplicationSnapshot(UserAccount user, StudentProfile studentProfile,
			Recommendation recommendation, JobPosting jobPosting) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("version", SNAPSHOT_VERSION);
		snapshot.put("student", createStudentSnapshot(user, studentProfile));
		snapshot.put("jobPosting", createJobPostingSnapshot(jobPosting));
		snapshot.put("recommendation", createRecommendationSnapshot(recommendation));
		return snapshot;
	}

	private Map<String, Object> createStudentSnapshot(UserAccount user, StudentProfile profile) {
		Map<String, Object> student = new LinkedHashMap<>();
		student.put("studentProfileId", profile.getId());
		student.put("name", user.getName());
		student.put("birthDate", stringValue(profile.getBirthDate()));
		student.put("nationality", profile.getNationality());
		student.put("currentRegion", profile.getCurrentRegion());
		student.put("koreanLevel", profile.getKoreanLevel().name());
		student.put("visaCode", profile.getVisaCode());
		student.put("universityName", profile.getUniversityName());
		student.put("academicState", profile.getAcademicState());
		student.put("topikLevel", profile.getTopikLevel());
		student.put("stayExpirationDate", stringValue(profile.getStayExpirationDate()));
		student.put("profileImageKey", profile.getProfileImageKey());
		student.put("careers", createCareerSnapshot(profile.getId()));
		student.put("availableWorkTimes", createStudentWorkTimeSnapshot(profile.getId()));
		return student;
	}

	private List<Map<String, Object>> createCareerSnapshot(Long studentProfileId) {
		return studentCareerRepository.findAllByUserIdOrderByStartMonthDescIdDesc(studentProfileId).stream()
				.map(career -> {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("careerName", career.getCareerName());
					item.put("startMonth", career.getStartMonth());
					item.put("endMonth", career.getEndMonth());
					item.put("description", career.getDescription());
					return item;
				})
				.toList();
	}

	private List<Map<String, Object>> createStudentWorkTimeSnapshot(Long studentProfileId) {
		return studentPreferredWorkTimeRepository.findAllByUserId(studentProfileId).stream()
				.sorted(Comparator.comparing(StudentPreferredWorkTime::getDayOfWeek)
						.thenComparing(StudentPreferredWorkTime::getStartTime))
				.map(time -> createWorkTimeSnapshot(time.getDayOfWeek(), time.getStartTime().toString(),
						time.getEndTime().toString()))
				.toList();
	}

	private Map<String, Object> createJobPostingSnapshot(JobPosting posting) {
		Map<String, Object> jobPosting = new LinkedHashMap<>();
		jobPosting.put("jobPostingId", posting.getId());
		jobPosting.put("workplaceId", posting.getWorkplace().getId());
		jobPosting.put("workplaceName", posting.getWorkplace().getWorkplaceName());
		jobPosting.put("workplaceAddress", posting.getWorkplace().getWorkplaceAddress());
		jobPosting.put("businessTypeId", posting.getBusinessType() == null ? null : posting.getBusinessType().getId());
		jobPosting.put("businessTypeName", posting.getBusinessType() == null ? null : posting.getBusinessType().getName());
		jobPosting.put("title", posting.getTitle());
		jobPosting.put("jobRole", posting.getJobRole());
		jobPosting.put("wageType", posting.getWageType());
		jobPosting.put("wageAmount", posting.getWageAmount());
		jobPosting.put("recruitmentCount", posting.getRecruitmentCount());
		jobPosting.put("workStartDate", stringValue(posting.getWorkStartDate()));
		jobPosting.put("requiredKoreanLevel", posting.getRequiredKoreanLevel());
		jobPosting.put("preferredExperience", posting.getPreferredExperience());
		jobPosting.put("workTimeNegotiable", posting.getWorkTimeNegotiable());
		jobPosting.put("applicationDeadlineDate", stringValue(posting.getApplicationDeadlineDate()));
		List<JobPostingWorkTime> workTimes = new ArrayList<>(
				jobPostingWorkTimeRepository.findAllByJobPostingIdIn(List.of(posting.getId())));
		workTimes.sort(Comparator.comparing(JobPostingWorkTime::getDayOfWeek)
				.thenComparing(JobPostingWorkTime::getStartTime));
		jobPosting.put("workTimes", workTimes.stream()
				.map(time -> createWorkTimeSnapshot(time.getDayOfWeek(), time.getStartTime().toString(),
						time.getEndTime().toString()))
				.toList());
		return jobPosting;
	}

	private Map<String, Object> createRecommendationSnapshot(Recommendation recommendation) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("recommendationId", recommendation.getId());
		snapshot.put("rank", recommendation.getRank());
		snapshot.put("score", recommendation.getScore());
		snapshot.put("reason", recommendation.getReasonJson());
		return snapshot;
	}

	private Map<String, Object> createWorkTimeSnapshot(String dayOfWeek, String startTime, String endTime) {
		Map<String, Object> workTime = new LinkedHashMap<>();
		workTime.put("dayOfWeek", dayOfWeek);
		workTime.put("startTime", startTime);
		workTime.put("endTime", endTime);
		return workTime;
	}

	private String stringValue(Object value) {
		return value == null ? null : value.toString();
	}
}
