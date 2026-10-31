package com.hirelink.domain.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import com.hirelink.domain.application.dto.EmployerApplicationDetailResponse;
import com.hirelink.domain.application.dto.JobPostingApplicationListResponse;
import com.hirelink.domain.application.dto.StudentApplicationDetailResponse;
import com.hirelink.domain.application.dto.StudentApplicationListResponse;
import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.employmentmatch.entity.EmploymentMatch;
import com.hirelink.domain.employmentmatch.repository.EmploymentMatchRepository;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.employmentoffer.repository.EmploymentOfferRepository;
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
import com.hirelink.global.storage.SupabaseStorageClient;

@Service
public class ApplicationService {
	private static final int MAX_ACTIVE_APPLICATIONS = 5;
	private static final List<ApplicationStatus> LIST_APPLICATION_STATUSES = List.of(
			ApplicationStatus.WAITING_RESPONSE, ApplicationStatus.OFFERED, ApplicationStatus.COMPLETED);
	private static final short SNAPSHOT_VERSION = 1;
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<ApplicationStatus> ACTIVE_APPLICATION_STATUSES = List.of(
			ApplicationStatus.WAITING_RESPONSE, ApplicationStatus.OFFERED);

	private final UserAccountRepository userAccountRepository;
	private final ApplicationRepository applicationRepository;
	private final RecommendationRepository recommendationRepository;
	private final JobPostingRepository jobPostingRepository;
	private final JobPostingWorkTimeRepository jobPostingWorkTimeRepository;
	private final StudentCareerRepository studentCareerRepository;
	private final StudentPreferredWorkTimeRepository studentPreferredWorkTimeRepository;
	private final EmploymentOfferRepository employmentOfferRepository;
	private final EmploymentMatchRepository employmentMatchRepository;
	private final SupabaseStorageClient storageClient;

	public ApplicationService(UserAccountRepository userAccountRepository,
			ApplicationRepository applicationRepository, RecommendationRepository recommendationRepository,
			JobPostingRepository jobPostingRepository, JobPostingWorkTimeRepository jobPostingWorkTimeRepository,
			StudentCareerRepository studentCareerRepository,
			StudentPreferredWorkTimeRepository studentPreferredWorkTimeRepository,
			EmploymentOfferRepository employmentOfferRepository,
			EmploymentMatchRepository employmentMatchRepository, SupabaseStorageClient storageClient) {
		this.userAccountRepository = userAccountRepository;
		this.applicationRepository = applicationRepository;
		this.recommendationRepository = recommendationRepository;
		this.jobPostingRepository = jobPostingRepository;
		this.jobPostingWorkTimeRepository = jobPostingWorkTimeRepository;
		this.studentCareerRepository = studentCareerRepository;
		this.studentPreferredWorkTimeRepository = studentPreferredWorkTimeRepository;
		this.employmentOfferRepository = employmentOfferRepository;
		this.employmentMatchRepository = employmentMatchRepository;
		this.storageClient = storageClient;
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

	@Transactional(readOnly = true)
	public EmployerApplicationDetailResponse getEmployerApplication(Long userId, Long applicationId) {
		UserAccount employer = getActiveUser(userId);
		if (employer.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 지원자 상세를 조회할 수 있습니다.");
		}
		Application application = applicationRepository.findById(applicationId)
				.orElseThrow(ApplicationNotFoundException::new);
		if (!application.getJobPosting().getWorkplace().getEmployerUser().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 채용공고에 접수된 지원만 조회할 수 있습니다.");
		}

		Map<String, Object> snapshot = application.getApplicationSnapshot();
		Map<String, Object> student = object(snapshot, "student");
		Map<String, Object> posting = object(snapshot, "jobPosting");
		Map<String, Object> recommendation = object(snapshot, "recommendation");
		return new EmployerApplicationDetailResponse(application.getId(), application.getJobPosting().getId(),
				application.getStatus(), application.getDesiredStartDate(), application.getAppliedAt(),
				employerResponseDeadline(application), employerApplicant(student),
				new EmployerApplicationDetailResponse.JobRequirements(workTimes(posting),
						integerValue(posting, "wageAmount"), stringValue(posting, "requiredKoreanLevel")),
				matchResult(recommendation));
	}

	@Transactional(readOnly = true)
	public JobPostingApplicationListResponse getJobPostingApplications(Long userId, Long jobPostingId,
			String statusValue) {
		UserAccount employer = getActiveUser(userId);
		if (employer.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 공고별 지원자 목록을 조회할 수 있습니다.");
		}
		JobPosting posting = jobPostingRepository.findById(jobPostingId)
				.orElseThrow(JobPostingNotFoundException::new);
		if (!posting.getWorkplace().getEmployerUser().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 채용공고만 조회할 수 있습니다.");
		}
		ApplicationStatus status = parseListStatus(statusValue);
		List<Application> applications = status == null
				? applicationRepository.findAllByJobPostingIdAndStatusInOrderByAppliedAtDescIdDesc(
						jobPostingId, LIST_APPLICATION_STATUSES)
				: applicationRepository.findAllByJobPostingIdAndStatusOrderByAppliedAtDescIdDesc(jobPostingId, status);
		long waitingCount = applicationRepository.countByJobPostingIdAndStatus(
				jobPostingId, ApplicationStatus.WAITING_RESPONSE);
		long offeredCount = applicationRepository.countByJobPostingIdAndStatus(
				jobPostingId, ApplicationStatus.OFFERED);
		long completedCount = applicationRepository.countByJobPostingIdAndStatus(
				jobPostingId, ApplicationStatus.COMPLETED);
		return new JobPostingApplicationListResponse(jobPostingId, MAX_ACTIVE_APPLICATIONS,
				waitingCount + offeredCount, waitingCount, offeredCount, completedCount,
				applications.stream().map(this::jobPostingApplicationItem).toList());
	}

	@Transactional(readOnly = true)
	public StudentApplicationDetailResponse getStudentApplication(Long userId, Long applicationId) {
		UserAccount student = getActiveUser(userId);
		if (student.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 본인 지원을 조회할 수 있습니다.");
		}
		Application application = applicationRepository.findById(applicationId)
				.orElseThrow(ApplicationNotFoundException::new);
		if (!application.getStudentProfile().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 지원만 조회할 수 있습니다.");
		}
		return studentApplicationDetail(application);
	}

	@Transactional(readOnly = true)
	public StudentApplicationListResponse getStudentApplications(Long userId, String statusValue) {
		UserAccount student = getActiveUser(userId);
		if (student.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 본인 지원 목록을 조회할 수 있습니다.");
		}
		ApplicationStatus status = parseListStatus(statusValue);
		List<Application> applications = status == null
				? applicationRepository.findAllByStudentProfileIdAndStatusInOrderByAppliedAtDescIdDesc(
						userId, LIST_APPLICATION_STATUSES)
				: applicationRepository.findAllByStudentProfileIdAndStatusOrderByAppliedAtDescIdDesc(userId, status);
		return new StudentApplicationListResponse(
				applications.stream().map(this::studentApplicationItem).toList());
	}

	private EmployerApplicationDetailResponse.Applicant employerApplicant(Map<String, Object> student) {
		return new EmployerApplicationDetailResponse.Applicant(stringValue(student, "name"), profileImageUrl(student),
				stringValue(student, "nationality"), stringValue(student, "visaCode"),
				localDateValue(student, "stayExpirationDate"), stringValue(student, "koreanLevel"),
				shortValue(student, "topikLevel"), careers(student), availableWorkTimes(student));
	}

	private List<EmployerApplicationDetailResponse.Career> careers(Map<String, Object> student) {
		Object value = student.get("careers");
		if (!(value instanceof List<?> items)) {
			return List.of();
		}
		List<EmployerApplicationDetailResponse.Career> careers = new ArrayList<>();
		for (Object item : items) {
			if (item instanceof Map<?, ?> raw) {
				careers.add(new EmployerApplicationDetailResponse.Career(stringValue(raw, "careerName"),
						stringValue(raw, "startMonth"), stringValue(raw, "endMonth"),
						stringValue(raw, "description")));
			}
		}
		return List.copyOf(careers);
	}

	private List<EmployerApplicationDetailResponse.WorkTime> availableWorkTimes(Map<String, Object> student) {
		return employerWorkTimes(student, "availableWorkTimes");
	}

	private List<EmployerApplicationDetailResponse.WorkTime> workTimes(Map<String, Object> posting) {
		return employerWorkTimes(posting, "workTimes");
	}

	private List<EmployerApplicationDetailResponse.WorkTime> employerWorkTimes(Map<String, Object> source,
			String key) {
		Object value = source.get(key);
		if (!(value instanceof List<?> items)) {
			return List.of();
		}
		List<EmployerApplicationDetailResponse.WorkTime> times = new ArrayList<>();
		for (Object item : items) {
			if (item instanceof Map<?, ?> raw) {
				times.add(new EmployerApplicationDetailResponse.WorkTime(stringValue(raw, "dayOfWeek"),
						LocalTime.parse(stringValue(raw, "startTime")), LocalTime.parse(stringValue(raw, "endTime"))));
			}
		}
		return List.copyOf(times);
	}

	private EmployerApplicationDetailResponse.MatchResult matchResult(Map<String, Object> recommendation) {
		Map<String, Object> reason = object(recommendation, "reason");
		return new EmployerApplicationDetailResponse.MatchResult(decimalValue(recommendation, "score"),
				booleanValue(reason, "workTimeMatched"), booleanValue(reason, "experienceMatched"),
				booleanValue(reason, "locationMatched"), booleanValue(reason, "wageMatched"),
				booleanValue(reason, "koreanLevelMatched"));
	}

	private JobPostingApplicationListResponse.Item jobPostingApplicationItem(Application application) {
		Map<String, Object> snapshot = application.getApplicationSnapshot();
		Map<String, Object> student = object(snapshot, "student");
		Map<String, Object> recommendation = object(snapshot, "recommendation");
		ApplicationLinks links = links(application);
		return new JobPostingApplicationListResponse.Item(application.getId(), application.getStatus(),
				stringValue(student, "name"), profileImageUrl(student), stringValue(student, "nationality"),
				stringValue(student, "visaCode"), shortValue(student, "topikLevel"),
				decimalValue(recommendation, "score"), application.getDesiredStartDate(), application.getAppliedAt(),
				employerResponseDeadline(application), links.offer() == null ? null : links.offer().getId(),
				links.offer() == null ? null : links.offer().getStudentResponseDeadline(),
				links.match() == null ? null : links.match().getId());
	}

	private StudentApplicationDetailResponse studentApplicationDetail(Application application) {
		Map<String, Object> posting = object(application.getApplicationSnapshot(), "jobPosting");
		ApplicationLinks links = links(application);
		EmploymentOffer offer = links.offer();
		return new StudentApplicationDetailResponse(application.getId(), application.getStatus(),
				application.getJobPosting().getId(), stringValue(posting, "title"),
				stringValue(posting, "workplaceName"), stringValue(posting, "workplaceAddress"),
				stringValue(posting, "wageType"), integerValue(posting, "wageAmount"),
				studentWorkTimes(posting), application.getDesiredStartDate(), application.getAppliedAt(),
				employerResponseDeadline(application), offer == null ? null : offer.getId(),
				offer == null ? null : offer.getStatus(), offer == null ? null : offer.getOfferedAt(),
				offer == null ? null : offer.getStudentResponseDeadline(),
				offer == null ? null : offer.getRespondedAt(), links.match() == null ? null : links.match().getId(),
				application.getStatus() == ApplicationStatus.ENDED ? application.getEndReason() : null,
				application.getStatus() == ApplicationStatus.ENDED ? application.getEndedAt() : null);
	}

	private StudentApplicationListResponse.Item studentApplicationItem(Application application) {
		Map<String, Object> snapshot = application.getApplicationSnapshot();
		Map<String, Object> posting = object(snapshot, "jobPosting");
		Map<String, Object> recommendation = object(snapshot, "recommendation");
		ApplicationLinks links = links(application);
		EmploymentOffer offer = links.offer();
		return new StudentApplicationListResponse.Item(application.getId(), application.getStatus(),
				application.getJobPosting().getId(), stringValue(posting, "title"),
				stringValue(posting, "workplaceName"), stringValue(posting, "wageType"),
				integerValue(posting, "wageAmount"), recommendationReasons(recommendation),
				application.getDesiredStartDate(), application.getAppliedAt(), employerResponseDeadline(application),
				offer == null ? null : offer.getId(), offer == null ? null : offer.getStatus(),
				offer == null ? null : offer.getOfferedAt(),
				offer == null ? null : offer.getStudentResponseDeadline(),
				links.match() == null ? null : links.match().getId());
	}

	private List<StudentApplicationDetailResponse.WorkTime> studentWorkTimes(Map<String, Object> posting) {
		Object value = posting.get("workTimes");
		if (!(value instanceof List<?> items)) {
			return List.of();
		}
		List<StudentApplicationDetailResponse.WorkTime> times = new ArrayList<>();
		for (Object item : items) {
			if (item instanceof Map<?, ?> raw) {
				times.add(new StudentApplicationDetailResponse.WorkTime(stringValue(raw, "dayOfWeek"),
						LocalTime.parse(stringValue(raw, "startTime")), LocalTime.parse(stringValue(raw, "endTime"))));
			}
		}
		return List.copyOf(times);
	}

	private List<String> recommendationReasons(Map<String, Object> recommendation) {
		Object reason = recommendation.get("reason");
		if (reason instanceof List<?> items) {
			return stringList(items);
		}
		if (!(reason instanceof Map<?, ?> values)) {
			return List.of();
		}
		for (String key : List.of("recommendationReasons", "reasons")) {
			Object item = values.get(key);
			if (item instanceof List<?> items) {
				return stringList(items);
			}
		}
		Object summary = values.get("summary");
		return summary == null ? List.of() : List.of(summary.toString());
	}

	private List<String> stringList(List<?> items) {
		return items.stream().filter(item -> item != null).map(Object::toString).toList();
	}

	private ApplicationLinks links(Application application) {
		EmploymentOffer offer = employmentOfferRepository.findByApplicationId(application.getId()).orElse(null);
		EmploymentMatch match = offer == null ? null
				: employmentMatchRepository.findByEmploymentOfferId(offer.getId()).orElse(null);
		return new ApplicationLinks(offer, match);
	}

	private Instant employerResponseDeadline(Application application) {
		return application.getStatus() == ApplicationStatus.WAITING_RESPONSE
				? application.getEmployerResponseDeadline() : null;
	}

	private ApplicationStatus parseListStatus(String value) {
		if (value == null) {
			return null;
		}
		try {
			ApplicationStatus status = ApplicationStatus.valueOf(value);
			if (!LIST_APPLICATION_STATUSES.contains(status)) {
				throw new InvalidApplicationException("지원 상태 필터가 올바르지 않습니다.");
			}
			return status;
		}
		catch (IllegalArgumentException exception) {
			throw new InvalidApplicationException("지원 상태 필터가 올바르지 않습니다.");
		}
	}

	private String profileImageUrl(Map<String, Object> student) {
		String key = stringValue(student, "profileImageKey");
		return key == null ? null : storageClient.createSignedUrl(key);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> object(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value instanceof Map<?, ?> ? (Map<String, Object>)value : Map.of();
	}

	private String stringValue(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value == null ? null : value.toString();
	}

	private Integer integerValue(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value instanceof Number number ? number.intValue() : null;
	}

	private Short shortValue(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value instanceof Number number ? number.shortValue() : null;
	}

	private BigDecimal decimalValue(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value == null ? null : new BigDecimal(value.toString());
	}

	private LocalDate localDateValue(Map<?, ?> source, String key) {
		String value = stringValue(source, key);
		return value == null ? null : LocalDate.parse(value);
	}

	private boolean booleanValue(Map<String, Object> source, String key) {
		Object value = source.get(key);
		if (value instanceof Boolean result) {
			return result;
		}
		for (String nestedKey : List.of("matchResult", "conditions")) {
			Object nested = source.get(nestedKey);
			if (nested instanceof Map<?, ?> values && values.get(key) instanceof Boolean result) {
				return result;
			}
		}
		return false;
	}

	private UserAccount getActiveUser(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		return user;
	}

	private record ApplicationLinks(EmploymentOffer offer, EmploymentMatch match) {
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
