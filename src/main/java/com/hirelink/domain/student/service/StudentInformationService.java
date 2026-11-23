package com.hirelink.domain.student.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.auth.service.PhoneVerificationService;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentInformationUpdateRequest;
import com.hirelink.domain.student.dto.StudentProfileUpdateRequest;
import com.hirelink.domain.student.dto.StudentProfileUpdateResponse;
import com.hirelink.domain.student.entity.StudentPreferredLocation;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudentCareerRepository;
import com.hirelink.domain.student.repository.StudentPreferredLocationRepository;
import com.hirelink.global.exception.InvalidStudentProfileException;
import com.hirelink.global.exception.PhoneNotVerifiedException;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentInformationService {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private final UserAccountRepository users;
	private final StudentProfileRepository profiles;
	private final StudentCareerRepository careers;
	private final StudentPreferredLocationRepository locations;
	private final StudentProfileService profileService;
	private final StudentCareerService careerService;
	private final PhoneVerificationService phones;

	public StudentInformationService(UserAccountRepository users, StudentProfileRepository profiles,
			StudentCareerRepository careers, StudentPreferredLocationRepository locations,
			StudentProfileService profileService, StudentCareerService careerService, PhoneVerificationService phones) {
		this.users = users;
		this.profiles = profiles;
		this.careers = careers;
		this.locations = locations;
		this.profileService = profileService;
		this.careerService = careerService;
		this.phones = phones;
	}

	@Transactional
	public StudentProfileUpdateResponse save(Long userId, StudentInformationUpdateRequest request) {
		var user = users.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 본인의 정보만 수정할 수 있습니다.");
		}
		var profile = profiles.findById(userId).orElseThrow(StudentProfileNotFoundException::new);
		if (!request.hasChanges()) {
			throw new InvalidStudentProfileException("수정할 항목을 입력해주세요.");
		}
		var birth = request.getBirthDate() == null ? profile.getBirthDate() : request.getBirthDate();
		if (birth == null || birth.isAfter(LocalDate.now(KOREA_ZONE).minusYears(18))) {
			throw new InvalidStudentProfileException("만 18세 이상만 프로필을 수정할 수 있습니다.");
		}
		Instant phoneDeadline = null;
		if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
			phoneDeadline = phones.applyVerifiedChange(user, request.getPhoneNumber());
		}
		if (request.getCareers() != null) {
			careers.deleteAll(careers.findAllByUserIdOrderByStartMonthDescIdDesc(userId));
			careers.flush();
			request.getCareers().forEach(career -> careerService.createCareer(userId, career));
		}
		if (request.getPreferredLocation() != null) {
			var value = request.getPreferredLocation();
			var location = locations.findByUserId(userId).orElseGet(() -> new StudentPreferredLocation(userId));
			location.updateLocation(value.locationName(), value.latitude(), value.longitude(), value.radiusM());
			locations.save(location);
		}
		var result = profileService.updateProfile(userId, StudentProfileUpdateRequest.forOnboarding(birth, profile.getNationality(),
				request.getCurrentRegion() == null ? profile.getCurrentRegion() : request.getCurrentRegion(),
				request.getKoreanLevel() == null ? profile.getKoreanLevel().name() : request.getKoreanLevel()));
		profiles.flush();
		if (phoneDeadline != null && !phoneDeadline.isAfter(Instant.now())) {
			throw new PhoneNotVerifiedException();
		}
		return result;
	}
}
