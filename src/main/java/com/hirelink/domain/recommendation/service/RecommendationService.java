package com.hirelink.domain.recommendation.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.entity.JobPostingWorkTime;
import com.hirelink.domain.jobposting.repository.JobPostingWorkTimeRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.recommendation.dto.RecommendationCreateResponse;
import com.hirelink.domain.recommendation.dto.RecommendationDetailResponse;
import com.hirelink.domain.recommendation.dto.RecommendationListResponse;
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
	private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

	private final UserAccountRepository userAccountRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final RecommendationBatchRepository batchRepository;
	private final RecommendationRepository recommendationRepository;
	private final JobPostingWorkTimeRepository workTimeRepository;
	private final RecommendationCalculationService calculationService;
	private final RecommendationValidityChecker validityChecker;

	public RecommendationService(UserAccountRepository userAccountRepository,
			StudentProfileRepository studentProfileRepository, RecommendationBatchRepository batchRepository,
			RecommendationRepository recommendationRepository, JobPostingWorkTimeRepository workTimeRepository,
			RecommendationCalculationService calculationService,
			RecommendationValidityChecker validityChecker) {
		this.userAccountRepository = userAccountRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.batchRepository = batchRepository;
		this.recommendationRepository = recommendationRepository;
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
		Instant generatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		RecommendationBatch batch = batchRepository.save(new RecommendationBatch(
				profile, ALGORITHM_VERSION, calculation.inputSnapshot(), null, generatedAt));
		List<Recommendation> recommendations = new java.util.ArrayList<>();
		for (int index = 0; index < calculation.results().size(); index++) {
			RecommendationResult result = calculation.results().get(index);
			JobPosting posting = calculation.postings().get(result.jobPostingId());
			recommendations.add(new Recommendation(batch, posting, (short)(index + 1), result.matchScore(),
					validityChecker.reasonSnapshot(result), generatedAt));
		}
		recommendationRepository.saveAll(recommendations);
		batchRepository.flush();
		return new RecommendationCreateResponse(batch.getId(), batch.getCreatedAt(), recommendations.size());
	}

	@Transactional(readOnly = true)
	public RecommendationListResponse getRecommendations(Long userId) {
		validateStudent(getCurrentUser(userId));
		RecommendationBatch batch = latestBatch(userId);
		List<Recommendation> recommendations = recommendationRepository
				.findAllByBatchIdOrderByRankAsc(batch.getId());
		boolean refreshRequired = validityChecker.isRefreshRequired(userId, batch, recommendations);
		if (refreshRequired) {
			return new RecommendationListResponse(batch.getId(), batch.getCreatedAt(), true, List.of());
		}
		return new RecommendationListResponse(batch.getId(), batch.getCreatedAt(), false,
				recommendations.stream().limit(RecommendationPolicyV1.MAX_RECOMMENDATIONS)
						.map(this::listItem).toList());
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
		if (validityChecker.isRefreshRequired(userId, latestBatch, latestRecommendations)) {
			throw new RecommendationStateConflictException("현재 상태에 맞게 추천 갱신이 필요합니다.");
		}
		return detail(recommendation);
	}

	private RecommendationListResponse.Item listItem(Recommendation recommendation) {
		JobPosting posting = recommendation.getJobPosting();
		return new RecommendationListResponse.Item(recommendation.getId(), posting.getId(),
				recommendation.getRank().intValue(), posting.getTitle(), posting.getWorkplace().getWorkplaceName(),
				posting.getWorkplace().getWorkplaceAddress(), posting.getWageType(), posting.getWageAmount(),
				workTimes(posting.getId()), recommendation.getScore(),
				validityChecker.recommendationReasons(recommendation.getReasonJson()));
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
}
