package com.hirelink.domain.jobposting.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.jobposting.dto.JobPostingDetailResponse;
import com.hirelink.domain.jobposting.dto.JobPostingListResponse;
import com.hirelink.domain.jobposting.dto.JobPostingListResponse.JobPostingSummary;
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
import com.hirelink.global.exception.InvalidJobPostingException;
import com.hirelink.global.exception.JobPostingNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class JobPostingService {

	private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

	private final UserAccountRepository userAccountRepository;
	private final JobPostingRepository jobPostingRepository;
	private final JobPostingWorkTimeRepository workTimeRepository;

	public JobPostingService(UserAccountRepository userAccountRepository, JobPostingRepository jobPostingRepository,
			JobPostingWorkTimeRepository workTimeRepository) {
		this.userAccountRepository = userAccountRepository;
		this.jobPostingRepository = jobPostingRepository;
		this.workTimeRepository = workTimeRepository;
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
