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
import com.hirelink.domain.employmentmatch.entity.EmploymentMatch;
import com.hirelink.domain.employmentmatch.repository.EmploymentMatchRepository;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.global.exception.EmploymentMatchNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;
import com.hirelink.global.storage.SupabaseStorageClient;

@Service
public class EmploymentMatchService {

	private final UserAccountRepository userAccountRepository;
	private final EmploymentMatchRepository employmentMatchRepository;
	private final SupabaseStorageClient storageClient;

	public EmploymentMatchService(UserAccountRepository userAccountRepository,
			EmploymentMatchRepository employmentMatchRepository, SupabaseStorageClient storageClient) {
		this.userAccountRepository = userAccountRepository;
		this.employmentMatchRepository = employmentMatchRepository;
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
