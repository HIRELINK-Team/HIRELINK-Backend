package com.hirelink.domain.recommendation.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.entity.JobPostingWorkTime;
import com.hirelink.domain.jobposting.repository.JobPostingWorkTimeRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.recommendation.dto.RecommendationCreateResponse;
import com.hirelink.domain.recommendation.dto.RecommendationDetailResponse;
import com.hirelink.domain.recommendation.dto.RecommendationListResponse;
import com.hirelink.domain.recommendation.dto.RecommendationRefreshResponse;
import com.hirelink.domain.recommendation.dto.RecommendationWorkTimeResponse;
import com.hirelink.domain.recommendation.entity.Recommendation;
import com.hirelink.domain.recommendation.entity.RecommendationBatch;
import com.hirelink.domain.recommendation.repository.RecommendationBatchRepository;
import com.hirelink.domain.recommendation.repository.RecommendationRepository;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.global.exception.RecommendationNotFoundException;
import com.hirelink.global.exception.RecommendationStateConflictException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class RecommendationService {

	private static final String ALGORITHM_VERSION = "v1";
	private static final Duration AUTO_REFRESH_INTERVAL = Duration.ofHours(24);
	private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

	private final UserAccountRepository userAccountRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final RecommendationBatchRepository batchRepository;
	private final RecommendationRepository recommendationRepository;
	private final ApplicationRepository applicationRepository;
	private final JobPostingWorkTimeRepository workTimeRepository;
	private final RecommendationCalculationService calculationService;
	private final RecommendationValidityChecker validityChecker;

	public RecommendationService(UserAccountRepository userAccountRepository,
			StudentProfileRepository studentProfileRepository, RecommendationBatchRepository batchRepository,
			RecommendationRepository recommendationRepository, ApplicationRepository applicationRepository,
			JobPostingWorkTimeRepository workTimeRepository,
			RecommendationCalculationService calculationService,
			RecommendationValidityChecker validityChecker) {
		this.userAccountRepository = userAccountRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.batchRepository = batchRepository;
		this.recommendationRepository = recommendationRepository;
		this.applicationRepository = applicationRepository;
		this.workTimeRepository = workTimeRepository;
		this.calculationService = calculationService;
		this.validityChecker = validityChecker;
	}

	@Transactional
	public RecommendationCreateResponse createInitialRecommendations(Long userId) {
		UserAccount student = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		validateStudent(student);
		if (batchRepository.existsByStudentProfileId(userId)) {
			throw new RecommendationStateConflictException("최초 추천이 이미 생성되었습니다.");
		}
		StudentProfile profile = studentProfileRepository.findById(userId)
				.orElseThrow(() -> new RecommendationStateConflictException(
						"추천에 필요한 프로필, 매칭 조건, 우선순위를 모두 등록해주세요."));
		RecommendationCalculationService.Calculation calculation = calculationService.calculate(userId);
		SavedBatch saved = saveBatch(profile, calculation);
		return new RecommendationCreateResponse(saved.batch().getId(), saved.batch().getCreatedAt(),
				saved.recommendationCount());
	}

	@Transactional
	public RecommendationRefreshResponse refreshRecommendations(Long userId) {
		UserAccount student = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		validateStudent(student);
		RecommendationBatch currentBatch = latestBatch(userId);
		List<Recommendation> currentRecommendations = recommendationRepository
				.findAllByBatchIdOrderByRankAsc(currentBatch.getId());
		if (hasActiveApplication(loadApplications(userId, currentRecommendations))) {
			return new RecommendationRefreshResponse(currentBatch.getId(), currentBatch.getCreatedAt(),
					currentRecommendations.size(), false);
		}
		RecommendationCalculationService.Calculation calculation = calculationService.calculate(userId);
		if (!validityChecker.isRefreshRequired(currentBatch, currentRecommendations, calculation)) {
			return new RecommendationRefreshResponse(currentBatch.getId(), currentBatch.getCreatedAt(),
					currentRecommendations.size(), false);
		}

		Instant invalidatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		currentBatch.invalidate(invalidatedAt);
		SavedBatch saved = saveBatch(currentBatch.getStudentProfile(), calculation);
		return new RecommendationRefreshResponse(saved.batch().getId(), saved.batch().getCreatedAt(),
				saved.recommendationCount(), true);
	}

	@Transactional
	public RecommendationListResponse getRecommendations(Long userId) {
		UserAccount student = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		validateStudent(student);
		RecommendationBatch batch = latestBatch(userId);
		List<Recommendation> recommendations = recommendationRepository
				.findAllByBatchIdOrderByRankAsc(batch.getId());
		Map<Long, Application> applicationsByPostingId = loadApplications(userId, recommendations);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		if (isAutoRefreshExpired(batch.getCreatedAt(), now)) {
			RecommendationCalculationService.Calculation calculation = calculationService.calculate(userId);
			Map<Long, Application> currentApplicationsByPostingId = applicationsByPostingId;
			List<Recommendation> retainedRecommendations = recommendations.stream()
					.filter(recommendation -> isActiveApplication(
							currentApplicationsByPostingId.get(recommendation.getJobPosting().getId())))
					.limit(RecommendationPolicyV1.MAX_RECOMMENDATIONS)
					.toList();
			batch.invalidate(now);
			SavedBatch saved = saveBatch(batch.getStudentProfile(), calculation, retainedRecommendations, now);
			batch = saved.batch();
			recommendations = recommendationRepository.findAllByBatchIdOrderByRankAsc(batch.getId());
			applicationsByPostingId = loadApplications(userId, recommendations);
		}
		boolean refreshRequired = !hasActiveApplication(applicationsByPostingId)
				&& validityChecker.isRefreshRequired(userId, batch, recommendations);
		if (refreshRequired) {
			return new RecommendationListResponse(batch.getId(), batch.getCreatedAt(), true, List.of());
		}
		Map<Long, Application> responseApplicationsByPostingId = applicationsByPostingId;
		return new RecommendationListResponse(batch.getId(), batch.getCreatedAt(), false,
				recommendations.stream().limit(RecommendationPolicyV1.MAX_RECOMMENDATIONS)
						.map(recommendation -> listItem(recommendation, responseApplicationsByPostingId)).toList());
	}

	@Transactional(readOnly = true)
	public RecommendationDetailResponse getRecommendation(Long userId, Long recommendationId) {
		validateStudent(getCurrentUser(userId));
		Recommendation recommendation = recommendationRepository.findById(recommendationId)
				.orElseThrow(RecommendationNotFoundException::new);
		if (!recommendation.getBatch().getStudentProfile().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 추천만 조회할 수 있습니다.");
		}
		RecommendationBatch latestBatch = latestBatch(userId);
		if (!recommendation.getBatch().getId().equals(latestBatch.getId())) {
			throw new RecommendationStateConflictException("이전 추천 배치의 추천은 조회할 수 없습니다.");
		}
		List<Recommendation> latestRecommendations = recommendationRepository
				.findAllByBatchIdOrderByRankAsc(latestBatch.getId());
		Map<Long, Application> applicationsByPostingId = loadApplications(userId, latestRecommendations);
		if (!hasActiveApplication(applicationsByPostingId)
				&& validityChecker.isRefreshRequired(userId, latestBatch, latestRecommendations)) {
			throw new RecommendationStateConflictException("현재 상태에 맞게 추천 갱신이 필요합니다.");
		}
		return detail(recommendation);
	}

	private RecommendationListResponse.Item listItem(Recommendation recommendation,
			Map<Long, Application> applicationsByPostingId) {
		JobPosting posting = recommendation.getJobPosting();
		Application application = applicationsByPostingId.get(posting.getId());
		return new RecommendationListResponse.Item(recommendation.getId(), posting.getId(),
				application == null ? null : application.getId(),
				application == null ? null : application.getStatus(),
				recommendation.getRank().intValue(), posting.getTitle(), posting.getWorkplace().getWorkplaceName(),
				posting.getWorkplace().getWorkplaceAddress(), posting.getWageType(), posting.getWageAmount(),
				workTimes(posting.getId()), recommendation.getScore(),
				validityChecker.recommendationReasons(recommendation.getReasonJson()));
	}

	private Map<Long, Application> loadApplications(Long userId, List<Recommendation> recommendations) {
		List<Long> jobPostingIds = recommendations.stream()
				.map(recommendation -> recommendation.getJobPosting().getId())
				.toList();
		if (jobPostingIds.isEmpty()) {
			return Map.of();
		}
		return applicationRepository.findAllByStudentProfileIdAndJobPostingIdIn(userId, jobPostingIds).stream()
				.collect(Collectors.toMap(application -> application.getJobPosting().getId(), Function.identity()));
	}

	private boolean hasActiveApplication(Map<Long, Application> applicationsByPostingId) {
		return applicationsByPostingId.values().stream()
				.anyMatch(this::isActiveApplication);
	}

	private boolean isActiveApplication(Application application) {
		return application != null && (application.getStatus() == ApplicationStatus.WAITING_RESPONSE
				|| application.getStatus() == ApplicationStatus.OFFERED);
	}

	private SavedBatch saveBatch(StudentProfile profile, RecommendationCalculationService.Calculation calculation) {
		Instant generatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		return saveBatch(profile, calculation, List.of(), generatedAt);
	}

	private SavedBatch saveBatch(StudentProfile profile, RecommendationCalculationService.Calculation calculation,
			List<Recommendation> retainedRecommendations, Instant generatedAt) {
		RecommendationBatch batch = batchRepository.save(new RecommendationBatch(
				profile, ALGORITHM_VERSION, calculation.inputSnapshot(), null, generatedAt));
		List<Recommendation> recommendations = new java.util.ArrayList<>();
		for (Recommendation retained : retainedRecommendations) {
			recommendations.add(new Recommendation(batch, retained.getJobPosting(),
					(short)(recommendations.size() + 1), retained.getScore(), retained.getReasonJson(), generatedAt));
		}
		for (RecommendationResult result : calculation.results()) {
			if (recommendations.size() >= RecommendationPolicyV1.MAX_RECOMMENDATIONS) {
				break;
			}
			if (retainedRecommendations.stream()
					.anyMatch(retained -> retained.getJobPosting().getId().equals(result.jobPostingId()))) {
				continue;
			}
			JobPosting posting = calculation.postings().get(result.jobPostingId());
			recommendations.add(new Recommendation(batch, posting, (short)(recommendations.size() + 1), result.matchScore(),
					validityChecker.reasonSnapshot(result), generatedAt));
		}
		recommendationRepository.saveAll(recommendations);
		batchRepository.flush();
		return new SavedBatch(batch, recommendations.size());
	}

	static boolean isAutoRefreshExpired(Instant generatedAt, Instant now) {
		return !now.isBefore(generatedAt.plus(AUTO_REFRESH_INTERVAL));
	}

	private RecommendationDetailResponse detail(Recommendation recommendation) {
		JobPosting posting = recommendation.getJobPosting();
		RecommendationBatch batch = recommendation.getBatch();
		return new RecommendationDetailResponse(recommendation.getId(), batch.getId(), posting.getId(),
				recommendation.getRank().intValue(), batch.getCreatedAt(), posting.getTitle(),
				posting.getWorkplace().getWorkplaceName(), posting.getWorkplace().getWorkplaceAddress(),
				posting.getWageType(), posting.getWageAmount(), workTimes(posting.getId()), recommendation.getScore(),
				validityChecker.recommendationReasons(recommendation.getReasonJson()));
	}

	private List<RecommendationWorkTimeResponse> workTimes(Long jobPostingId) {
		return workTimeRepository.findAllByJobPostingIdIn(List.of(jobPostingId)).stream()
				.sorted(Comparator.comparingInt((JobPostingWorkTime time) -> WEEK_DAYS.indexOf(time.getDayOfWeek()))
						.thenComparing(JobPostingWorkTime::getStartTime)
						.thenComparing(JobPostingWorkTime::getEndTime))
				.map(time -> new RecommendationWorkTimeResponse(
						time.getDayOfWeek(), time.getStartTime(), time.getEndTime()))
				.toList();
	}

	private RecommendationBatch latestBatch(Long studentProfileId) {
		return batchRepository.findFirstByStudentProfileIdOrderByCreatedAtDescIdDesc(studentProfileId)
				.orElseThrow(RecommendationNotFoundException::new);
	}

	private UserAccount getCurrentUser(Long userId) {
		return userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
	}

	private void validateStudent(UserAccount user) {
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 추천을 이용할 수 있습니다.");
		}
	}

	private record SavedBatch(RecommendationBatch batch, int recommendationCount) {
	}
}
