package com.hirelink.domain.student.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentOnboardingRequest;
import com.hirelink.domain.student.dto.StudentOnboardingResponse;
import com.hirelink.domain.student.dto.StudentProfileCreateRequest;
import com.hirelink.domain.student.dto.StudentProfileUpdateRequest;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.entity.StudyProgramType;
import com.hirelink.domain.student.repository.StudyProgramRepository;
import com.hirelink.domain.student.entity.StudentRegistrationVerification;
import com.hirelink.domain.student.repository.StudentCareerRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.global.exception.PhoneNotVerifiedException;
import com.hirelink.global.exception.InvalidStudentProfileException;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentOnboardingService {

	private final UserAccountRepository users;
	private final StudentProfileRepository profiles;
	private final StudentRegistrationVerificationRepository verifications;
	private final StudentCareerRepository careers;
	private final StudentProfileService profileService;
	private final StudentCareerService careerService;
	private final StudentMatchingPreferenceService preferenceService;
	private final StudyProgramRepository studyPrograms;

	public StudentOnboardingService(UserAccountRepository users, StudentProfileRepository profiles,
			StudentRegistrationVerificationRepository verifications, StudentCareerRepository careers,
			StudentProfileService profileService, StudentCareerService careerService,
			StudentMatchingPreferenceService preferenceService, StudyProgramRepository studyPrograms) {
		this.users = users;
		this.profiles = profiles;
		this.verifications = verifications;
		this.careers = careers;
		this.profileService = profileService;
		this.careerService = careerService;
		this.preferenceService = preferenceService;
		this.studyPrograms = studyPrograms;
	}

	@Transactional
	public StudentOnboardingResponse complete(Long userId, StudentOnboardingRequest request) {
		UserAccount user = users.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 온보딩을 완료할 수 있습니다.");
		}
		if (!user.isPhoneVerified()) { throw new PhoneNotVerifiedException(); }
		StudentRegistrationVerification verification = verifications.findById(userId)
				.orElseThrow(() -> new SocialAuthException(HttpStatus.CONFLICT, "REGISTRATION_VERIFICATION_REQUIRED",
						"등록증 인증을 거친 가입 정보가 필요합니다."));
		StudentProfile profile = profiles.findById(userId).orElse(null);
		if (profile != null && profile.getOnboardingCompletedAt() != null) {
			throw new SocialAuthException(HttpStatus.CONFLICT, "ONBOARDING_ALREADY_COMPLETED",
					"이미 온보딩을 완료했습니다. 개별 수정 API를 이용해주세요.");
		}
		LocalDate birthDate = verification.getBirthDate();
		StudyProgramType selectedProgram = StudyProgramType.valueOf(request.getStudyProgram());
		if (!StudyProgramType.supportsRegistration(verification.getVisaCode())) {
			throw new InvalidStudentProfileException("등록증에서 확인한 체류자격이 D-2여야 합니다.");
		}
		var program = studyPrograms.findByProgramName(selectedProgram.getProgramName())
				.orElseThrow(() -> new IllegalStateException("지원 학업과정 기준 데이터가 없습니다."));
		if (birthDate != null && request.getBirthDate() != null && !birthDate.equals(request.getBirthDate())) {
			throw new InvalidStudentProfileException("가입 시 확인한 생년월일은 온보딩에서 변경할 수 없습니다.");
		}
		if (birthDate == null) { birthDate = request.getBirthDate(); }
		if (birthDate == null) { throw new InvalidStudentProfileException("생년월일을 입력해주세요."); }
		if (profile == null) {
			profileService.createProfile(userId, new StudentProfileCreateRequest(user.getName(), birthDate,
					verification.getNationality(), request.getCurrentRegion(), request.getKoreanLevel()));
			profile = profiles.findById(userId).orElseThrow();
		}
		profileService.updateProfile(userId, StudentProfileUpdateRequest.forOnboarding(birthDate,
				verification.getNationality(), request.getCurrentRegion(), request.getKoreanLevel()));
		if (profile.getStayStartDate() != null && verification.getStayExpiresOn().isBefore(profile.getStayStartDate())) {
			throw new InvalidStudentProfileException("체류 만료일은 체류 시작일보다 빠를 수 없습니다.");
		}
		profile.updateVerifiedStudyProgram(program.getId(), selectedProgram.getVisaCode(), verification.getStayExpiresOn());
		careers.deleteAll(careers.findAllByUserIdOrderByStartMonthDescIdDesc(userId));
		careers.flush();
		request.getCareers().forEach(career -> careerService.createCareer(userId, career));
		preferenceService.updatePreferences(userId, request.getMatchingPreferences());
		profile.completeOnboarding(Instant.now().truncatedTo(ChronoUnit.MICROS));
		profiles.flush();
		return new StudentOnboardingResponse(userId, true, profile.getOnboardingCompletedAt());
	}
}
