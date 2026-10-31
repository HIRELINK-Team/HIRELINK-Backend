package com.hirelink.domain.employmentmatch.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.employmentmatch.dto.EmploymentMatchDetailResponse;
import com.hirelink.domain.employmentmatch.dto.EmploymentMatchListResponse;
import com.hirelink.domain.employmentmatch.entity.EmploymentMatch;
import com.hirelink.domain.employmentmatch.repository.EmploymentMatchRepository;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.jobposting.entity.JobPosting;
import com.hirelink.domain.jobposting.repository.JobPostingRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.global.exception.EmploymentMatchNotFoundException;
import com.hirelink.global.exception.InvalidApplicationException;
import com.hirelink.global.exception.JobPostingNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;
import com.hirelink.global.storage.SupabaseStorageClient;

@Service
public class EmploymentMatchService {

	private final UserAccountRepository userAccountRepository;
	private final EmploymentMatchRepository employmentMatchRepository;
	private final JobPostingRepository jobPostingRepository;
	private final SupabaseStorageClient storageClient;

	public EmploymentMatchService(UserAccountRepository userAccountRepository,
			EmploymentMatchRepository employmentMatchRepository, JobPostingRepository jobPostingRepository,
			SupabaseStorageClient storageClient) {
		this.userAccountRepository = userAccountRepository;
		this.employmentMatchRepository = employmentMatchRepository;
		this.jobPostingRepository = jobPostingRepository;
		this.storageClient = storageClient;
	}

	@Transactional(readOnly = true)
	public EmploymentMatchDetailResponse getMatch(Long userId, Long matchId) {
		UserAccount viewer = getActiveUser(userId);
		EmploymentMatch match = employmentMatchRepository.findById(matchId)
				.orElseThrow(EmploymentMatchNotFoundException::new);
		EmploymentOffer offer = match.getEmploymentOffer();
		Application application = offer.getApplication();
		Long studentId = application.getStudentProfile().getId();
		UserAccount employer = application.getJobPosting().getWorkplace().getEmployerUser();

		boolean studentViewer = viewer.getRole() == MemberRole.STUDENT && studentId.equals(userId);
		boolean employerViewer = viewer.getRole() == MemberRole.EMPLOYER && employer.getId().equals(userId);
		if (!studentViewer && !employerViewer) {
			throw new AccessDeniedException("채용완료 당사자만 조회할 수 있습니다.");
		}

		UserAccount studentUser = userAccountRepository.findById(studentId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		Map<String, Object> snapshot = application.getApplicationSnapshot();
		Map<String, Object> jobPostingSnapshot = object(snapshot, "jobPosting");
		Map<String, Object> studentSnapshot = object(snapshot, "student");

		UserAccount contactUser = studentViewer ? employer : studentUser;
		EmploymentMatchDetailResponse.Contact contact = new EmploymentMatchDetailResponse.Contact(
				contactUser.getName(), formatPhoneNumber(contactUser.getPhoneNumber()));
		EmploymentMatchDetailResponse.Student student = employerViewer
				? createStudent(application.getStudentProfile(), studentSnapshot) : null;

		return new EmploymentMatchDetailResponse(match.getId(), application.getId(), offer.getId(),
				longValue(jobPostingSnapshot, "jobPostingId"), stringValue(jobPostingSnapshot, "title"),
				stringValue(jobPostingSnapshot, "workplaceName"),
				stringValue(jobPostingSnapshot, "workplaceAddress"), stringValue(jobPostingSnapshot, "wageType"),
				integerValue(jobPostingSnapshot, "wageAmount"), workTimes(jobPostingSnapshot),
				application.getDesiredStartDate(), match.getCompletedAt(), contact, student);
	}

	@Transactional(readOnly = true)
	public EmploymentMatchListResponse getMyMatches(Long userId, Long jobPostingId) {
		UserAccount viewer = getActiveUser(userId);
		boolean employerViewer = viewer.getRole() == MemberRole.EMPLOYER;
		List<EmploymentMatch> matches;
		if (viewer.getRole() == MemberRole.STUDENT) {
			if (jobPostingId != null) {
				throw new InvalidApplicationException("유학생은 채용공고 필터를 사용할 수 없습니다.");
			}
			matches = employmentMatchRepository
					.findAllByEmploymentOfferApplicationStudentProfileIdOrderByCompletedAtDescIdDesc(userId);
		}
		else if (employerViewer) {
			if (jobPostingId == null) {
				matches = employmentMatchRepository
						.findAllByEmploymentOfferApplicationJobPostingWorkplaceEmployerUserIdOrderByCompletedAtDescIdDesc(
								userId);
			}
			else {
				if (jobPostingId <= 0) {
					throw new InvalidApplicationException("채용공고 ID가 올바르지 않습니다.");
				}
				JobPosting posting = jobPostingRepository.findById(jobPostingId)
						.orElseThrow(JobPostingNotFoundException::new);
				if (!posting.getWorkplace().getEmployerUser().getId().equals(userId)) {
					throw new AccessDeniedException("본인 소유의 채용공고만 필터로 사용할 수 있습니다.");
				}
				matches = employmentMatchRepository
						.findAllByEmploymentOfferApplicationJobPostingIdOrderByCompletedAtDescIdDesc(jobPostingId);
			}
		}
		else {
			throw new AccessDeniedException("채용완료 목록 조회 권한이 없습니다.");
		}
		return new EmploymentMatchListResponse(matches.stream()
				.map(match -> matchListItem(match, employerViewer)).toList());
	}

	private EmploymentMatchListResponse.Item matchListItem(EmploymentMatch match, boolean employerViewer) {
		EmploymentOffer offer = match.getEmploymentOffer();
		Application application = offer.getApplication();
		Map<String, Object> snapshot = application.getApplicationSnapshot();
		Map<String, Object> posting = object(snapshot, "jobPosting");
		Map<String, Object> studentSnapshot = object(snapshot, "student");
		EmploymentMatchListResponse.Student student = employerViewer
				? createListStudent(application.getStudentProfile(), studentSnapshot) : null;
		return new EmploymentMatchListResponse.Item(match.getId(), application.getId(), offer.getId(),
				longValue(posting, "jobPostingId"), stringValue(posting, "title"),
				stringValue(posting, "workplaceName"), stringValue(posting, "wageType"),
				integerValue(posting, "wageAmount"), application.getDesiredStartDate(), match.getCompletedAt(), student);
	}

	private EmploymentMatchListResponse.Student createListStudent(StudentProfile profile,
			Map<String, Object> studentSnapshot) {
		String imageUrl = profile.getProfileImageKey() == null ? null
				: storageClient.createSignedUrl(profile.getProfileImageKey());
		return new EmploymentMatchListResponse.Student(stringValue(studentSnapshot, "name"), imageUrl,
				stringValue(studentSnapshot, "nationality"), stringValue(studentSnapshot, "visaCode"),
				shortValue(studentSnapshot, "topikLevel"));
	}

	private UserAccount getActiveUser(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		return user;
	}

	private EmploymentMatchDetailResponse.Student createStudent(StudentProfile profile,
			Map<String, Object> studentSnapshot) {
		String imageUrl = profile.getProfileImageKey() == null ? null
				: storageClient.createSignedUrl(profile.getProfileImageKey());
		return new EmploymentMatchDetailResponse.Student(stringValue(studentSnapshot, "name"), imageUrl,
				stringValue(studentSnapshot, "nationality"), stringValue(studentSnapshot, "visaCode"),
				shortValue(studentSnapshot, "topikLevel"));
	}

	private List<EmploymentMatchDetailResponse.WorkTime> workTimes(Map<String, Object> jobPostingSnapshot) {
		Object value = jobPostingSnapshot.get("workTimes");
		if (!(value instanceof List<?> items)) {
			return List.of();
		}
		List<EmploymentMatchDetailResponse.WorkTime> workTimes = new ArrayList<>();
		for (Object item : items) {
			if (item instanceof Map<?, ?> raw) {
				workTimes.add(new EmploymentMatchDetailResponse.WorkTime(
						stringValue(raw, "dayOfWeek"), LocalTime.parse(stringValue(raw, "startTime")),
						LocalTime.parse(stringValue(raw, "endTime"))));
			}
		}
		return List.copyOf(workTimes);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> object(Map<String, Object> source, String key) {
		Object value = source.get(key);
		return value instanceof Map<?, ?> ? (Map<String, Object>)value : Map.of();
	}

	private String stringValue(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value == null ? null : value.toString();
	}

	private Long longValue(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value instanceof Number number ? number.longValue() : null;
	}

	private Integer integerValue(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value instanceof Number number ? number.intValue() : null;
	}

	private Short shortValue(Map<?, ?> source, String key) {
		Object value = source.get(key);
		return value instanceof Number number ? number.shortValue() : null;
	}

	private String formatPhoneNumber(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.length() != 11) {
			return phoneNumber;
		}
		return phoneNumber.substring(0, 3) + "-" + phoneNumber.substring(3, 7) + "-" + phoneNumber.substring(7);
	}
}
