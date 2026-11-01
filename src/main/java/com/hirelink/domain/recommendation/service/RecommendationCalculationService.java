package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.entity.JobPostingWorkTime;
import com.hirelink.domain.jobposting.repository.JobPostingRepository;
import com.hirelink.domain.jobposting.repository.JobPostingWorkTimeRepository;
import com.hirelink.domain.student.entity.StudentJobPreference;
import com.hirelink.domain.student.entity.StudentPreferredJob;
import com.hirelink.domain.student.entity.StudentPreferredLocation;
import com.hirelink.domain.student.entity.StudentPreferredWorkTime;
import com.hirelink.domain.student.entity.StudentPriority;
import com.hirelink.domain.student.repository.StudentJobPreferenceRepository;
import com.hirelink.domain.student.repository.StudentPreferredJobRepository;
import com.hirelink.domain.student.repository.StudentPreferredLocationRepository;
import com.hirelink.domain.student.repository.StudentPreferredWorkTimeRepository;
import com.hirelink.domain.student.repository.StudentPriorityRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.global.exception.RecommendationStateConflictException;

@Component
public class RecommendationCalculationService {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<ApplicationStatus> ACTIVE_REVIEW_STATUSES = List.of(
			ApplicationStatus.WAITING_RESPONSE, ApplicationStatus.OFFERED);
	private static final Set<RecommendationCriterion> REQUIRED_CRITERIA = Set.of(
			RecommendationCriterion.WORK_TIME, RecommendationCriterion.WAGE,
			RecommendationCriterion.DISTANCE, RecommendationCriterion.BUSINESS_TYPE);

	private final StudentProfileRepository studentProfileRepository;
	private final StudentJobPreferenceRepository jobPreferenceRepository;
	private final StudentPreferredJobRepository preferredJobRepository;
	private final StudentPreferredWorkTimeRepository preferredWorkTimeRepository;
	private final StudentPreferredLocationRepository preferredLocationRepository;
	private final StudentPriorityRepository priorityRepository;
	private final JobPostingRepository jobPostingRepository;
	private final JobPostingWorkTimeRepository workTimeRepository;
	private final ApplicationRepository applicationRepository;
	private final RecommendationGenerator recommendationGenerator;

	public RecommendationCalculationService(StudentProfileRepository studentProfileRepository,
			StudentJobPreferenceRepository jobPreferenceRepository,
			StudentPreferredJobRepository preferredJobRepository,
			StudentPreferredWorkTimeRepository preferredWorkTimeRepository,
			StudentPreferredLocationRepository preferredLocationRepository,
			StudentPriorityRepository priorityRepository, JobPostingRepository jobPostingRepository,
			JobPostingWorkTimeRepository workTimeRepository, ApplicationRepository applicationRepository,
			RecommendationGenerator recommendationGenerator) {
		this.studentProfileRepository = studentProfileRepository;
		this.jobPreferenceRepository = jobPreferenceRepository;
		this.preferredJobRepository = preferredJobRepository;
		this.preferredWorkTimeRepository = preferredWorkTimeRepository;
		this.preferredLocationRepository = preferredLocationRepository;
		this.priorityRepository = priorityRepository;
		this.jobPostingRepository = jobPostingRepository;
		this.workTimeRepository = workTimeRepository;
		this.applicationRepository = applicationRepository;
		this.recommendationGenerator = recommendationGenerator;
	}

	public Calculation calculate(Long studentProfileId) {
		RecommendationPreference preference = loadPreference(studentProfileId);
		Map<String, Object> inputSnapshot = createInputSnapshot(studentProfileId, preference);
		Map<Long, JobPosting> postings = jobPostingRepository.findAllForRecommendation().stream()
				.collect(Collectors.toMap(JobPosting::getId, Function.identity(), (first, second) -> first,
						LinkedHashMap::new));
		List<RecommendationCandidate> candidates = createCandidates(studentProfileId, postings);
		List<RecommendationResult> results = recommendationGenerator.generate(
				candidates, preference, LocalDate.now(KOREA_ZONE));
		return new Calculation(inputSnapshot, results, postings);
	}

	private RecommendationPreference loadPreference(Long studentProfileId) {
		if (!studentProfileRepository.existsById(studentProfileId)) {
			throw incompleteProfile();
		}
		StudentJobPreference jobPreference = jobPreferenceRepository.findById(studentProfileId)
				.orElseThrow(this::incompleteProfile);
		List<StudentPreferredJob> preferredJobs = preferredJobRepository.findAllByUserId(studentProfileId);
		List<StudentPreferredWorkTime> preferredWorkTimes = preferredWorkTimeRepository
				.findAllByUserId(studentProfileId).stream()
				.sorted(Comparator.comparing(StudentPreferredWorkTime::getDayOfWeek)
						.thenComparing(StudentPreferredWorkTime::getStartTime)
						.thenComparing(StudentPreferredWorkTime::getEndTime))
				.toList();
		StudentPreferredLocation location = preferredLocationRepository.findByUserId(studentProfileId)
				.orElseThrow(this::incompleteProfile);
		List<StudentPriority> priorities = priorityRepository
				.findAllByIdUserIdOrderByPriorityOrderAsc(studentProfileId);

		if (!jobPreference.isBusinessTypeFlexible() && preferredJobs.isEmpty() || preferredWorkTimes.isEmpty()
				|| location.getRadiusM() == null || location.getRadiusM() <= 0
				|| !DistanceScoreCalculator.hasValidCoordinates(location.getLatitude(), location.getLongitude())) {
			throw incompleteProfile();
		}
		List<RecommendationTimeRange> availableWorkTimes = preferredWorkTimes.stream()
				.map(time -> new RecommendationTimeRange(time.getDayOfWeek(), time.getStartTime(), time.getEndTime()))
				.toList();
		if (!WorkTimeNormalizer.allValid(availableWorkTimes)) {
			throw incompleteProfile();
		}
		List<RecommendationCriterion> criteria;
		try {
			criteria = priorities.stream()
					.map(priority -> RecommendationCriterion.valueOf(priority.getCriterion().getCriterionName()))
					.toList();
		} catch (IllegalArgumentException exception) {
			throw incompleteProfile();
		}
		if (criteria.size() != REQUIRED_CRITERIA.size() || !new HashSet<>(criteria).equals(REQUIRED_CRITERIA)
				|| jobPreference.getMinimumHourlyWage() != null && jobPreference.getMinimumHourlyWage() <= 0) {
			throw incompleteProfile();
		}

		List<RecommendationPreference.PreferredBusinessType> businessTypes = preferredJobs.stream()
				.map(job -> new RecommendationPreference.PreferredBusinessType(
						job.getBusinessType().getName(), job.getOtherJobName()))
				.toList();
		return new RecommendationPreference(availableWorkTimes, location.getLatitude(), location.getLongitude(),
				location.getRadiusM(), jobPreference.getMinimumHourlyWage(),
				jobPreference.isBusinessTypeFlexible(), businessTypes, criteria);
	}

	private List<RecommendationCandidate> createCandidates(Long studentProfileId, Map<Long, JobPosting> postings) {
		List<Long> postingIds = new ArrayList<>(postings.keySet());
		Map<Long, List<RecommendationTimeRange>> workTimesByPosting = new HashMap<>();
		if (!postingIds.isEmpty()) {
			for (JobPostingWorkTime time : workTimeRepository.findAllByJobPostingIdIn(postingIds)) {
				workTimesByPosting.computeIfAbsent(time.getJobPosting().getId(), ignored -> new ArrayList<>())
						.add(new RecommendationTimeRange(time.getDayOfWeek(), time.getStartTime(), time.getEndTime()));
			}
		}
		Set<Long> appliedPostingIds = new HashSet<>(
				applicationRepository.findAppliedJobPostingIds(studentProfileId));
		Map<Long, Long> activeReviewCounts = new HashMap<>();
		for (Object[] row : applicationRepository.countActiveReviewsByJobPosting(ACTIVE_REVIEW_STATUSES)) {
			activeReviewCounts.put(((Number)row[0]).longValue(), ((Number)row[1]).longValue());
		}
		return postings.values().stream()
				.map(posting -> new RecommendationCandidate(posting.getId(), posting.getStatus(),
						posting.getApplicationDeadlineDate(), activeReviewCounts.getOrDefault(posting.getId(), 0L),
						appliedPostingIds.contains(posting.getId()), posting.getWorkplace().getLatitude(),
						posting.getWorkplace().getLongitude(), posting.getWageType(), posting.getWageAmount(),
						posting.getBusinessType() == null ? null : posting.getBusinessType().getName(),
						posting.getOtherJobName(), workTimesByPosting.getOrDefault(posting.getId(), List.of())))
				.toList();
	}

	private Map<String, Object> createInputSnapshot(Long studentProfileId, RecommendationPreference preference) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("version", "1");
		snapshot.put("studentProfileId", studentProfileId.toString());
		snapshot.put("businessTypeFlexible", preference.businessTypeFlexible());
		snapshot.put("preferredJobs", preference.preferredBusinessTypes().stream()
				.map(job -> {
					Map<String, Object> value = new LinkedHashMap<>();
					value.put("businessTypeName", job.businessTypeName());
					value.put("otherJobName", job.otherJobName());
					return value;
				})
				.toList());
		snapshot.put("preferredWorkTimes", preference.availableWorkTimes().stream()
				.map(time -> workTimeSnapshot(time.dayOfWeek(), time.startTime().toString(), time.endTime().toString()))
				.toList());
		Map<String, Object> location = new LinkedHashMap<>();
		location.put("latitude", decimalSnapshot(preference.latitude()));
		location.put("longitude", decimalSnapshot(preference.longitude()));
		location.put("radiusM", preference.radiusM().toString());
		snapshot.put("preferredLocation", location);
		snapshot.put("minimumHourlyWage", preference.minimumHourlyWage() == null
				? null : preference.minimumHourlyWage().toString());
		snapshot.put("priorities", preference.priorities().stream().map(Enum::name).toList());
		return snapshot;
	}

	private Map<String, Object> workTimeSnapshot(String dayOfWeek, String startTime, String endTime) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("dayOfWeek", dayOfWeek);
		value.put("startTime", startTime);
		value.put("endTime", endTime);
		return value;
	}

	private String decimalSnapshot(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	private RecommendationStateConflictException incompleteProfile() {
		return new RecommendationStateConflictException("추천에 필요한 프로필, 매칭 조건, 우선순위를 모두 등록해주세요.");
	}

	public record Calculation(
			Map<String, Object> inputSnapshot,
			List<RecommendationResult> results,
			Map<Long, JobPosting> postings) {
	}
}
