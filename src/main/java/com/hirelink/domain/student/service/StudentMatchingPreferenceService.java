package com.hirelink.domain.student.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse.PreferredJob;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse.PreferredLocation;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse.PreferredWorkTime;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse.Priority;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceUpdateRequest;
import com.hirelink.domain.student.entity.PriorityCriterion;
import com.hirelink.domain.student.entity.StudentJobPreference;
import com.hirelink.domain.student.entity.StudentPreferredJob;
import com.hirelink.domain.student.entity.StudentPreferredLocation;
import com.hirelink.domain.student.entity.StudentPreferredWorkTime;
import com.hirelink.domain.student.entity.StudentPriority;
import com.hirelink.domain.student.repository.PriorityCriterionRepository;
import com.hirelink.domain.student.repository.StudentJobPreferenceRepository;
import com.hirelink.domain.student.repository.StudentPreferredJobRepository;
import com.hirelink.domain.student.repository.StudentPreferredLocationRepository;
import com.hirelink.domain.student.repository.StudentPreferredWorkTimeRepository;
import com.hirelink.domain.student.repository.StudentPriorityRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.workplace.entity.BusinessType;
import com.hirelink.domain.workplace.repository.BusinessTypeRepository;
import com.hirelink.global.exception.InvalidMatchingPreferenceException;
import com.hirelink.global.exception.MatchingReferenceNotFoundException;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentMatchingPreferenceService {

	private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
	private static final Set<String> PRIORITY_CRITERIA = Set.of("WORK_TIME", "WAGE", "DISTANCE", "BUSINESS_TYPE");

	private final UserAccountRepository userAccountRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final StudentJobPreferenceRepository jobPreferenceRepository;
	private final StudentPreferredJobRepository preferredJobRepository;
	private final StudentPreferredWorkTimeRepository preferredWorkTimeRepository;
	private final StudentPreferredLocationRepository preferredLocationRepository;
	private final StudentPriorityRepository priorityRepository;
	private final BusinessTypeRepository businessTypeRepository;
	private final PriorityCriterionRepository priorityCriterionRepository;

	public StudentMatchingPreferenceService(UserAccountRepository userAccountRepository,
			StudentProfileRepository studentProfileRepository, StudentJobPreferenceRepository jobPreferenceRepository,
			StudentPreferredJobRepository preferredJobRepository, StudentPreferredWorkTimeRepository preferredWorkTimeRepository,
			StudentPreferredLocationRepository preferredLocationRepository, StudentPriorityRepository priorityRepository,
			BusinessTypeRepository businessTypeRepository, PriorityCriterionRepository priorityCriterionRepository) {
		this.userAccountRepository = userAccountRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.jobPreferenceRepository = jobPreferenceRepository;
		this.preferredJobRepository = preferredJobRepository;
		this.preferredWorkTimeRepository = preferredWorkTimeRepository;
		this.preferredLocationRepository = preferredLocationRepository;
		this.priorityRepository = priorityRepository;
		this.businessTypeRepository = businessTypeRepository;
		this.priorityCriterionRepository = priorityCriterionRepository;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public StudentMatchingPreferenceResponse getPreferences(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		validateStudent(user);
		return readPreferences(userId);
	}

	@Transactional
	public StudentMatchingPreferenceResponse updatePreferences(Long userId, StudentMatchingPreferenceUpdateRequest request) {
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		validateStudent(user);
		if (!request.hasChanges()) {
			throw new InvalidMatchingPreferenceException("수정할 항목을 입력해주세요.");
		}
		Map<Long, BusinessType> businessTypes = validateJobs(request);
		validateWorkTimes(request.getPreferredWorkTimes());
		Map<Long, PriorityCriterion> criteria = validatePriorities(request.getPriorities());

		if (request.getBusinessTypeFlexible() != null || request.hasMinimumHourlyWage()) {
			StudentJobPreference preference = jobPreferenceRepository.findById(userId)
					.orElseGet(() -> new StudentJobPreference(userId));
			if (request.getBusinessTypeFlexible() != null) {
				preference.updateBusinessTypeFlexible(request.getBusinessTypeFlexible());
			}
			if (request.hasMinimumHourlyWage()) {
				preference.updateMinimumHourlyWage(request.getMinimumHourlyWage());
			}
			jobPreferenceRepository.save(preference);
		}
		if (request.getPreferredJobs() != null) {
			preferredJobRepository.deleteAllByUserId(userId);
			preferredJobRepository.saveAll(request.getPreferredJobs().stream()
					.map(job -> new StudentPreferredJob(userId, businessTypes.get(job.businessTypeId()), job.otherJobName()))
					.toList());
		}
		if (request.getPreferredWorkTimes() != null) {
			preferredWorkTimeRepository.deleteAllByUserId(userId);
			preferredWorkTimeRepository.saveAll(request.getPreferredWorkTimes().stream()
					.map(time -> new StudentPreferredWorkTime(userId, time.dayOfWeek(), time.startTime(), time.endTime()))
					.toList());
		}
		if (request.getPreferredLocation() != null) {
			StudentMatchingPreferenceUpdateRequest.PreferredLocation value = request.getPreferredLocation();
			StudentPreferredLocation location = preferredLocationRepository.findByUserId(userId)
					.orElseGet(() -> new StudentPreferredLocation(userId));
			location.updateLocation(value.locationName(), value.latitude(), value.longitude(), value.radiusM());
			preferredLocationRepository.save(location);
		}
		if (request.getPriorities() != null) {
			replacePriorities(userId, request.getPriorities(), criteria);
		}
		return readPreferences(userId);
	}

	private void validateStudent(UserAccount user) {
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 매칭 조건을 관리할 수 있습니다.");
		}
		if (!studentProfileRepository.existsById(user.getId())) {
			throw new StudentProfileNotFoundException();
		}
	}

	private StudentMatchingPreferenceResponse readPreferences(Long userId) {
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

	private Map<Long, BusinessType> validateJobs(StudentMatchingPreferenceUpdateRequest request) {
		Boolean flexible = request.getBusinessTypeFlexible();
		List<StudentMatchingPreferenceUpdateRequest.PreferredJob> jobs = request.getPreferredJobs();
		if (flexible == null && jobs == null) {
			return Map.of();
		}
		if (flexible == null || jobs == null) {
			throw new InvalidMatchingPreferenceException("업종 상관없음 여부와 희망 업종 목록을 함께 전달해주세요.");
		}
		if (flexible && !jobs.isEmpty() || !flexible && jobs.isEmpty()) {
			throw new InvalidMatchingPreferenceException("업종 상관없음은 빈 목록으로, 업종 선택은 한 개 이상 전달해주세요.");
		}
		Set<Long> ids = new HashSet<>();
		for (StudentMatchingPreferenceUpdateRequest.PreferredJob job : jobs) {
			if (!ids.add(job.businessTypeId())) {
				throw new InvalidMatchingPreferenceException("같은 업종을 중복해서 전달할 수 없습니다.");
			}
		}
		Map<Long, BusinessType> businessTypes = businessTypeRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(BusinessType::getId, value -> value));
		if (businessTypes.size() != ids.size()) {
			throw new MatchingReferenceNotFoundException();
		}
		for (StudentMatchingPreferenceUpdateRequest.PreferredJob job : jobs) {
			if ("OTHER".equals(businessTypes.get(job.businessTypeId()).getName())) {
				if (job.otherJobName() == null || job.otherJobName().isBlank()) {
					throw new InvalidMatchingPreferenceException("기타 업종명을 입력해주세요.");
				}
			} else if (job.otherJobName() != null) {
				throw new InvalidMatchingPreferenceException("기타 업종에서만 직접 입력한 업종명을 사용할 수 있습니다.");
			}
		}
		return businessTypes;
	}

	private void validateWorkTimes(List<StudentMatchingPreferenceUpdateRequest.PreferredWorkTime> workTimes) {
		if (workTimes == null) {
			return;
		}
		List<StudentMatchingPreferenceUpdateRequest.PreferredWorkTime> sorted = workTimes.stream()
				.sorted(Comparator.comparing(StudentMatchingPreferenceUpdateRequest.PreferredWorkTime::dayOfWeek)
						.thenComparing(StudentMatchingPreferenceUpdateRequest.PreferredWorkTime::startTime))
				.toList();
		Map<String, Integer> previousEnds = new HashMap<>();
		for (StudentMatchingPreferenceUpdateRequest.PreferredWorkTime time : sorted) {
			int start = time.startTime().toSecondOfDay() / 60;
			// 종료의 00:00은 해당 요일이 끝나는 자정이다.
			int end = time.endTime().equals(LocalTime.MIDNIGHT) ? 24 * 60 : time.endTime().toSecondOfDay() / 60;
			if (time.startTime().equals(time.endTime()) || end <= start) {
				throw new InvalidMatchingPreferenceException("종료시간은 시작시간보다 이후여야 합니다. 자정을 넘으면 요일별로 나눠주세요.");
			}
			if (start < previousEnds.getOrDefault(time.dayOfWeek(), -1)) {
				throw new InvalidMatchingPreferenceException("같은 요일의 근무시간은 겹칠 수 없습니다.");
			}
			previousEnds.put(time.dayOfWeek(), end);
		}
	}

	private Map<Long, PriorityCriterion> validatePriorities(List<StudentMatchingPreferenceUpdateRequest.Priority> priorities) {
		if (priorities == null) {
			return Map.of();
		}
		Set<Long> ids = new HashSet<>();
		Set<Integer> orders = new HashSet<>();
		for (StudentMatchingPreferenceUpdateRequest.Priority priority : priorities) {
			if (!ids.add(priority.criterionId()) || !orders.add(priority.priorityOrder())) {
				throw new InvalidMatchingPreferenceException("우선순위 기준과 순위는 중복될 수 없습니다.");
			}
		}
		Map<Long, PriorityCriterion> criteria = priorityCriterionRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(PriorityCriterion::getId, value -> value));
		if (criteria.size() != ids.size()) {
			throw new MatchingReferenceNotFoundException();
		}
		Set<String> names = criteria.values().stream().map(PriorityCriterion::getCriterionName).collect(Collectors.toSet());
		if (!names.equals(PRIORITY_CRITERIA)) {
			throw new InvalidMatchingPreferenceException("근무시간·시급·거리·업종의 우선순위를 모두 전달해주세요.");
		}
		return criteria;
	}

	private void replacePriorities(Long userId, List<StudentMatchingPreferenceUpdateRequest.Priority> priorities,
			Map<Long, PriorityCriterion> criteria) {
		List<StudentPriority> existing = priorityRepository.findAllByIdUserIdOrderByPriorityOrderAsc(userId);
		Map<Long, StudentPriority> previous = existing.stream()
				.collect(Collectors.toMap(value -> value.getCriterion().getId(), value -> value));
		List<StudentPriority> replacements = new ArrayList<>();
		for (StudentMatchingPreferenceUpdateRequest.Priority value : priorities) {
			StudentPriority old = previous.get(value.criterionId());
			BigDecimal weight = old != null && old.getPriorityOrder().intValue() == value.priorityOrder() ? old.getWeight() : null;
			replacements.add(new StudentPriority(userId, criteria.get(value.criterionId()), (short) value.priorityOrder(), weight));
		}
		// 기존 순위를 먼저 삭제하여 1·2순위 교환 시 UNIQUE 충돌을 방지한다.
		priorityRepository.deleteAll(existing);
		priorityRepository.flush();
		priorityRepository.saveAll(replacements);
	}
}
