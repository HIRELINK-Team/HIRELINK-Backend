package com.hirelink.domain.jobposting.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.jobposting.dto.JobPostingCreateRequest;
import com.hirelink.domain.jobposting.dto.JobPostingCreateResponse;
import com.hirelink.domain.jobposting.dto.JobPostingDetailResponse;
import com.hirelink.domain.jobposting.dto.JobPostingListResponse;
import com.hirelink.domain.jobposting.dto.JobPostingListResponse.JobPostingSummary;
import com.hirelink.domain.jobposting.dto.JobPostingUpdateRequest;
import com.hirelink.domain.jobposting.dto.JobPostingUpdateResponse;
import com.hirelink.domain.jobposting.dto.JobPostingWorkTimeRequest;
import com.hirelink.domain.jobposting.dto.JobPostingWorkTimeResponse;
import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.entity.JobPostingStatus;
import com.hirelink.domain.jobposting.entity.JobPostingWorkTime;
import com.hirelink.domain.jobposting.repository.JobPostingRepository;
import com.hirelink.domain.jobposting.repository.JobPostingWorkTimeRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.workplace.entity.BusinessType;
import com.hirelink.domain.workplace.entity.Workplace;
import com.hirelink.domain.workplace.repository.BusinessTypeRepository;
import com.hirelink.domain.workplace.repository.WorkplaceRepository;
import com.hirelink.global.exception.BusinessTypeNotFoundException;
import com.hirelink.global.exception.InvalidJobPostingException;
import com.hirelink.global.exception.JobPostingNotFoundException;
import com.hirelink.global.exception.JobPostingStateConflictException;
import com.hirelink.global.exception.WithdrawnAccountException;
import com.hirelink.global.exception.WorkplaceNotFoundException;

@Service
public class JobPostingService {

	private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final UserAccountRepository userAccountRepository;
	private final JobPostingRepository jobPostingRepository;
	private final JobPostingWorkTimeRepository workTimeRepository;
	private final WorkplaceRepository workplaceRepository;
	private final BusinessTypeRepository businessTypeRepository;

	public JobPostingService(UserAccountRepository userAccountRepository, JobPostingRepository jobPostingRepository,
			JobPostingWorkTimeRepository workTimeRepository, WorkplaceRepository workplaceRepository,
			BusinessTypeRepository businessTypeRepository) {
		this.userAccountRepository = userAccountRepository;
		this.jobPostingRepository = jobPostingRepository;
		this.workTimeRepository = workTimeRepository;
		this.workplaceRepository = workplaceRepository;
		this.businessTypeRepository = businessTypeRepository;
	}

	@Transactional
	public JobPostingCreateResponse createJobPosting(Long userId, JobPostingCreateRequest request) {
		UserAccount user = getCurrentUser(userId);
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 공고를 등록할 수 있습니다.");
		}
		Workplace workplace = workplaceRepository.findById(request.getWorkplaceId())
				.orElseThrow(WorkplaceNotFoundException::new);
		if (!workplace.getEmployerUser().getId().equals(userId)) {
			throw new AccessDeniedException("본인 사업장에만 공고를 등록할 수 있습니다.");
		}
		validateWorkTimes(request.getWorkTimes());
		BusinessType businessType = request.getBusinessTypeId() == null ? null
				: businessTypeRepository.findById(request.getBusinessTypeId()).orElseThrow(BusinessTypeNotFoundException::new);

		JobPosting posting = new JobPosting(workplace, request.getStatus(), businessType,
				createTitle(workplace.getWorkplaceName(), request.getJobRole()), request.getJobRole(),
				request.getWageType(), request.getWageAmount(),
				request.getRecruitmentCount() == null ? null : request.getRecruitmentCount().shortValue(),
				request.getWorkStartDate(), request.getRequiredKoreanLevel(), request.getPreferredExperience(),
				request.getWorkTimeNegotiable(), request.getApplicationDeadlineDate());
		if (posting.getStatus() == JobPostingStatus.RECRUITING) {
			validateRecruitingConditions(posting, !request.getWorkTimes().isEmpty());
			validateRecruitingDates(posting.getWorkStartDate(), posting.getApplicationDeadlineDate());
		}
		jobPostingRepository.save(posting);
		List<JobPostingWorkTime> workTimes = request.getWorkTimes().stream()
				.map(time -> new JobPostingWorkTime(posting, time.dayOfWeek(), time.startTime(), time.endTime()))
				.toList();
		workTimeRepository.saveAll(workTimes);
		return new JobPostingCreateResponse(posting.getId(), workplace.getId(), workplace.getWorkplaceName(),
				posting.getTitle(), businessType == null ? null : businessType.getId(),
				businessType == null ? null : businessType.getName(), posting.getJobRole(), request.getRecruitmentCount(),
				posting.getWageType(), posting.getWageAmount(), posting.getWorkStartDate(), posting.getRequiredKoreanLevel(),
				posting.getPreferredExperience(), posting.getApplicationDeadlineDate(), posting.getWorkTimeNegotiable(),
				posting.getStatus(), toWorkTimes(workTimes), posting.getCreatedAt());
	}

	@Transactional
	public JobPostingUpdateResponse updateJobPosting(Long userId, Long jobPostingId, JobPostingUpdateRequest request) {
		UserAccount user = getCurrentUser(userId);
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 공고를 수정할 수 있습니다.");
		}
		if (!request.hasChanges()) {
			throw new InvalidJobPostingException("수정할 항목을 입력해주세요.");
		}
		JobPosting posting = jobPostingRepository.findByIdForUpdate(jobPostingId)
				.orElseThrow(JobPostingNotFoundException::new);
		Workplace workplace = posting.getWorkplace();
		if (!workplace.getEmployerUser().getId().equals(userId)) {
			throw new AccessDeniedException("본인 사업장의 공고만 수정할 수 있습니다.");
		}
		JobPostingStatus previousStatus = posting.getStatus();
		JobPostingStatus status = request.getStatus() != null ? request.getStatus() : previousStatus;
		if (previousStatus == JobPostingStatus.CLOSED) {
			throw new JobPostingStateConflictException("마감한 공고는 수정할 수 없습니다.");
		}
		if (previousStatus == JobPostingStatus.RECRUITING && status == JobPostingStatus.DRAFT) {
			throw new JobPostingStateConflictException("모집 중인 공고는 임시저장으로 되돌릴 수 없습니다.");
		}

		BusinessType businessType = request.getBusinessTypeId() != null
				? businessTypeRepository.findById(request.getBusinessTypeId()).orElseThrow(BusinessTypeNotFoundException::new)
				: posting.getBusinessType();
		List<JobPostingWorkTime> workTimes = workTimeRepository.findAllByJobPostingIdIn(List.of(jobPostingId));
		if (request.getWorkTimes() != null) {
			validateWorkTimes(request.getWorkTimes());
		}
		String jobRole = request.getJobRole() != null ? request.getJobRole() : posting.getJobRole();
		posting.updateConditions(status, businessType, createTitle(workplace.getWorkplaceName(), jobRole), jobRole,
				request.getWageType() != null ? request.getWageType() : posting.getWageType(),
				request.getWageAmount() != null ? request.getWageAmount() : posting.getWageAmount(),
				request.getRecruitmentCount() != null ? Short.valueOf(request.getRecruitmentCount().shortValue()) : posting.getRecruitmentCount(),
				request.getWorkStartDate() != null ? request.getWorkStartDate() : posting.getWorkStartDate(),
				request.getRequiredKoreanLevel() != null ? request.getRequiredKoreanLevel() : posting.getRequiredKoreanLevel(),
				request.getPreferredExperience() != null ? request.getPreferredExperience() : posting.getPreferredExperience(),
				request.getWorkTimeNegotiable() != null ? request.getWorkTimeNegotiable() : posting.getWorkTimeNegotiable(),
				request.getApplicationDeadlineDate() != null ? request.getApplicationDeadlineDate() : posting.getApplicationDeadlineDate());
		if (status == JobPostingStatus.RECRUITING) {
			boolean hasWorkTimes = request.getWorkTimes() != null ? !request.getWorkTimes().isEmpty() : !workTimes.isEmpty();
			validateRecruitingConditions(posting, hasWorkTimes);
			// 모집을 처음 시작할 때는 두 날짜를, 모집 중 수정할 때는 이번에 받은 날짜만 검사한다.
			validateRecruitingDates(previousStatus == JobPostingStatus.DRAFT ? posting.getWorkStartDate() : request.getWorkStartDate(),
					previousStatus == JobPostingStatus.DRAFT ? posting.getApplicationDeadlineDate() : request.getApplicationDeadlineDate());
		}
		if (request.getWorkTimes() != null) {
			workTimeRepository.deleteAllInBatch(workTimes);
			workTimes = workTimeRepository.saveAll(request.getWorkTimes().stream()
					.map(time -> new JobPostingWorkTime(posting, time.dayOfWeek(), time.startTime(), time.endTime()))
					.toList());
		}
		jobPostingRepository.flush();
		return new JobPostingUpdateResponse(posting.getId(), workplace.getId(), workplace.getWorkplaceName(),
				posting.getTitle(), businessType == null ? null : businessType.getId(),
				businessType == null ? null : businessType.getName(), posting.getJobRole(),
				posting.getRecruitmentCount() == null ? null : posting.getRecruitmentCount().intValue(),
				posting.getWageType(), posting.getWageAmount(), posting.getWorkStartDate(), posting.getRequiredKoreanLevel(),
				posting.getPreferredExperience(), posting.getApplicationDeadlineDate(), posting.getWorkTimeNegotiable(),
				posting.getStatus(), toWorkTimes(workTimes), posting.getUpdatedAt());
	}

	@Transactional(readOnly = true)
	public JobPostingListResponse getMyJobPostings(Long userId, String status, String includeDraft) {
		UserAccount user = getCurrentUser(userId);
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 본인의 공고 목록을 조회할 수 있습니다.");
		}
		List<JobPostingStatus> statuses = resolveStatuses(status, includeDraft);
		List<JobPosting> postings = jobPostingRepository.findAllByEmployerUserIdAndStatusIn(userId, statuses);
		if (postings.isEmpty()) {
			return new JobPostingListResponse(List.of());
		}

		List<Long> postingIds = postings.stream().map(JobPosting::getId).toList();
		Map<Long, List<JobPostingWorkTime>> workTimesByPosting = workTimeRepository.findAllByJobPostingIdIn(postingIds).stream()
				.collect(Collectors.groupingBy(time -> time.getJobPosting().getId()));
		List<JobPostingSummary> summaries = postings.stream()
				.map(posting -> toSummary(posting, workTimesByPosting.getOrDefault(posting.getId(), List.of())))
				.toList();
		return new JobPostingListResponse(summaries);
	}

	@Transactional(readOnly = true)
	public JobPostingDetailResponse getJobPosting(Long userId, Long jobPostingId) {
		UserAccount user = getCurrentUser(userId);
		JobPosting posting = jobPostingRepository.findById(jobPostingId)
				.orElseThrow(JobPostingNotFoundException::new);
		Workplace workplace = posting.getWorkplace();
		if (user.getRole() == MemberRole.EMPLOYER) {
			if (!workplace.getEmployerUser().getId().equals(userId)) {
				throw new AccessDeniedException("본인 사업장의 공고만 조회할 수 있습니다.");
			}
		} else if (user.getRole() != MemberRole.STUDENT || posting.getStatus() != JobPostingStatus.RECRUITING) {
			throw new AccessDeniedException("유학생은 모집 중인 공고만 조회할 수 있습니다.");
		}

		BusinessType businessType = posting.getBusinessType();
		List<JobPostingWorkTime> workTimes = workTimeRepository.findAllByJobPostingIdIn(List.of(jobPostingId));
		return new JobPostingDetailResponse(posting.getId(), posting.getTitle(), posting.getStatus(),
				workplace.getId(), workplace.getWorkplaceName(), workplace.getWorkplaceAddress(),
				workplace.getLatitude(), workplace.getLongitude(),
				businessType == null ? null : businessType.getId(), businessType == null ? null : businessType.getName(),
				posting.getJobRole(), posting.getWageType(), posting.getWageAmount(),
				posting.getRecruitmentCount() == null ? null : posting.getRecruitmentCount().intValue(),
				posting.getWorkStartDate(), posting.getRequiredKoreanLevel(), posting.getPreferredExperience(),
				posting.getWorkTimeNegotiable(), posting.getApplicationDeadlineDate(), toWorkTimes(workTimes));
	}

	private void validateRecruitingConditions(JobPosting posting, boolean hasWorkTimes) {
		if (posting.getBusinessType() == null || posting.getJobRole() == null || posting.getRecruitmentCount() == null
				|| posting.getWageType() == null || posting.getWageAmount() == null || posting.getWorkStartDate() == null
				|| posting.getRequiredKoreanLevel() == null || posting.getPreferredExperience() == null
				|| posting.getApplicationDeadlineDate() == null || posting.getWorkTimeNegotiable() == null || !hasWorkTimes) {
			throw new InvalidJobPostingException("모집을 시작하려면 모든 근무조건과 근무시간을 입력해주세요.");
		}
	}

	private void validateRecruitingDates(LocalDate workStartDate, LocalDate applicationDeadlineDate) {
		LocalDate today = LocalDate.now(KOREA_ZONE);
		if ((workStartDate != null && workStartDate.isBefore(today))
				|| (applicationDeadlineDate != null && applicationDeadlineDate.isBefore(today))) {
			throw new InvalidJobPostingException("근무 시작일과 모집 마감일은 오늘 또는 이후 날짜여야 합니다.");
		}
	}

	private void validateWorkTimes(List<JobPostingWorkTimeRequest> workTimes) {
		List<JobPostingWorkTimeRequest> sorted = workTimes.stream()
				.sorted(Comparator.comparing(JobPostingWorkTimeRequest::dayOfWeek)
						.thenComparing(JobPostingWorkTimeRequest::startTime))
				.toList();
		Map<String, Integer> previousEnds = new HashMap<>();
		for (JobPostingWorkTimeRequest time : sorted) {
			int start = time.startTime().toSecondOfDay() / 60;
			// 종료의 00:00은 해당 요일이 끝나는 자정이다.
			int end = time.endTime().equals(LocalTime.MIDNIGHT) ? 24 * 60 : time.endTime().toSecondOfDay() / 60;
			if (time.startTime().equals(time.endTime()) || end <= start) {
				throw new InvalidJobPostingException("종료시간은 시작시간보다 이후여야 합니다. 자정을 넘으면 요일별로 나눠주세요.");
			}
			if (start < previousEnds.getOrDefault(time.dayOfWeek(), -1)) {
				throw new InvalidJobPostingException("같은 요일의 근무시간은 겹칠 수 없습니다.");
			}
			previousEnds.put(time.dayOfWeek(), end);
		}
	}

	private String createTitle(String workplaceName, String jobRole) {
		if (jobRole == null) {
			return null;
		}
		String title = workplaceName + " " + jobRole + " 모집";
		if (title.codePointCount(0, title.length()) > 200) {
			return title.substring(0, title.offsetByCodePoints(0, 199)) + "…";
		}
		return title;
	}

	private UserAccount getCurrentUser(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		return user;
	}

	private List<JobPostingStatus> resolveStatuses(String status, String includeDraft) {
		if (status != null && !status.equals("RECRUITING") && !status.equals("CLOSED")) {
			throw new InvalidJobPostingException("status는 RECRUITING 또는 CLOSED만 입력해주세요.");
		}
		if (includeDraft != null && !includeDraft.equals("true") && !includeDraft.equals("false")) {
			throw new InvalidJobPostingException("includeDraft는 true 또는 false만 입력해주세요.");
		}
		if (status != null) {
			return List.of(JobPostingStatus.valueOf(status));
		}
		if ("true".equals(includeDraft)) {
			return List.of(JobPostingStatus.DRAFT, JobPostingStatus.RECRUITING, JobPostingStatus.CLOSED);
		}
		return List.of(JobPostingStatus.RECRUITING, JobPostingStatus.CLOSED);
	}

	private JobPostingSummary toSummary(JobPosting posting, List<JobPostingWorkTime> workTimes) {
		return new JobPostingSummary(posting.getId(), posting.getWorkplace().getId(), posting.getWorkplace().getWorkplaceName(),
				posting.getJobRole(), posting.getWageType(), posting.getWageAmount(), posting.getStatus(),
				posting.getApplicationDeadlineDate(), toWorkTimes(workTimes), posting.getCreatedAt());
	}

	private List<JobPostingWorkTimeResponse> toWorkTimes(List<JobPostingWorkTime> workTimes) {
		return workTimes.stream()
				.sorted(Comparator.comparingInt((JobPostingWorkTime time) -> WEEK_DAYS.indexOf(time.getDayOfWeek()))
						.thenComparing(JobPostingWorkTime::getStartTime).thenComparing(JobPostingWorkTime::getId))
				.map(time -> new JobPostingWorkTimeResponse(time.getDayOfWeek(), time.getStartTime(), time.getEndTime()))
				.toList();
	}
}
