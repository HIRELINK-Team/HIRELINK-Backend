package com.hirelink.domain.student.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse.PreferredJob;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse.PreferredLocation;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse.PreferredWorkTime;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse.Priority;
import com.hirelink.domain.student.entity.StudentJobPreference;
import com.hirelink.domain.student.entity.StudentPreferredWorkTime;
import com.hirelink.domain.student.repository.StudentJobPreferenceRepository;
import com.hirelink.domain.student.repository.StudentPreferredJobRepository;
import com.hirelink.domain.student.repository.StudentPreferredLocationRepository;
import com.hirelink.domain.student.repository.StudentPreferredWorkTimeRepository;
import com.hirelink.domain.student.repository.StudentPriorityRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentMatchingPreferenceService {

	private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

	private final UserAccountRepository userAccountRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final StudentJobPreferenceRepository jobPreferenceRepository;
	private final StudentPreferredJobRepository preferredJobRepository;
	private final StudentPreferredWorkTimeRepository preferredWorkTimeRepository;
	private final StudentPreferredLocationRepository preferredLocationRepository;
	private final StudentPriorityRepository priorityRepository;

	public StudentMatchingPreferenceService(UserAccountRepository userAccountRepository,
			StudentProfileRepository studentProfileRepository, StudentJobPreferenceRepository jobPreferenceRepository,
			StudentPreferredJobRepository preferredJobRepository, StudentPreferredWorkTimeRepository preferredWorkTimeRepository,
			StudentPreferredLocationRepository preferredLocationRepository, StudentPriorityRepository priorityRepository) {
		this.userAccountRepository = userAccountRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.jobPreferenceRepository = jobPreferenceRepository;
		this.preferredJobRepository = preferredJobRepository;
		this.preferredWorkTimeRepository = preferredWorkTimeRepository;
		this.preferredLocationRepository = preferredLocationRepository;
		this.priorityRepository = priorityRepository;
	}

	@Transactional(readOnly = true)
	public StudentMatchingPreferenceResponse getPreferences(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 매칭 조건을 조회할 수 있습니다.");
		}
		if (!studentProfileRepository.existsById(userId)) {
			throw new StudentProfileNotFoundException();
		}

		StudentJobPreference preference = jobPreferenceRepository.findById(userId).orElse(null);
		boolean flexible = preference != null && preference.isBusinessTypeFlexible();
		Integer minimumHourlyWage = preference == null ? null : preference.getMinimumHourlyWage();
		List<PreferredJob> jobs = flexible ? List.of() : preferredJobRepository.findAllByUserId(userId).stream()
				.map(job -> new PreferredJob(job.getBusinessType().getId(), job.getBusinessType().getName(),
						"OTHER".equals(job.getBusinessType().getName()) ? job.getOtherJobName() : null))
				.toList();
		List<PreferredWorkTime> workTimes = preferredWorkTimeRepository.findAllByUserId(userId).stream()
				.sorted(Comparator.comparingInt((StudentPreferredWorkTime time) -> WEEK_DAYS.indexOf(time.getDayOfWeek()))
						.thenComparing(StudentPreferredWorkTime::getStartTime)
						.thenComparing(StudentPreferredWorkTime::getEndTime)
						.thenComparing(StudentPreferredWorkTime::getId))
				.map(time -> new PreferredWorkTime(time.getDayOfWeek(), time.getStartTime(), time.getEndTime()))
				.toList();
		PreferredLocation location = preferredLocationRepository.findByUserId(userId)
				.map(value -> new PreferredLocation(value.getLocationName(), value.getLatitude(), value.getLongitude(), value.getRadiusM()))
				.orElse(null);
		List<Priority> priorities = priorityRepository.findAllByIdUserIdOrderByPriorityOrderAsc(userId).stream()
				.map(value -> new Priority(value.getCriterion().getId(), value.getCriterion().getCriterionName(), value.getPriorityOrder()))
				.toList();
		return new StudentMatchingPreferenceResponse(flexible, jobs, workTimes, location, minimumHourlyWage, priorities);
	}
}
