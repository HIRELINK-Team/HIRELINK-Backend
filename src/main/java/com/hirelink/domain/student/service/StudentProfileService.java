package com.hirelink.domain.student.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentProfileCreateRequest;
import com.hirelink.domain.student.dto.StudentProfileCreateResponse;
import com.hirelink.domain.student.dto.StudentProfileResponse;
import com.hirelink.domain.student.dto.StudentProfileUpdateRequest;
import com.hirelink.domain.student.dto.StudentProfileUpdateResponse;
import com.hirelink.domain.student.entity.StudyProgramType;
import com.hirelink.domain.student.entity.KoreanLevel;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.repository.StudentCareerRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.VisaTypeRepository;
import com.hirelink.domain.student.repository.StudyProgramRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.global.exception.DuplicateStudentProfileException;
import com.hirelink.global.exception.InvalidStudentProfileException;
import com.hirelink.global.exception.PhoneNotVerifiedException;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;
import com.hirelink.global.storage.SupabaseStorageClient;

@Service
public class StudentProfileService {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

	private final UserAccountRepository userAccountRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final StudentCareerRepository studentCareerRepository;
	private final VisaTypeRepository visaTypeRepository;
	private final SupabaseStorageClient storageClient;
	private final StudyProgramRepository studyPrograms;
	private final StudentRegistrationVerificationRepository verifications;

	public StudentProfileService(UserAccountRepository userAccountRepository,
			StudentProfileRepository studentProfileRepository, StudentCareerRepository studentCareerRepository,
			VisaTypeRepository visaTypeRepository, SupabaseStorageClient storageClient,
			StudyProgramRepository studyPrograms, StudentRegistrationVerificationRepository verifications) {
		this.userAccountRepository = userAccountRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.studentCareerRepository = studentCareerRepository;
		this.visaTypeRepository = visaTypeRepository;
		this.storageClient = storageClient;
		this.studyPrograms = studyPrograms;
		this.verifications = verifications;
	}

	@Transactional(readOnly = true)
	public StudentProfileResponse getProfile(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 프로필을 조회할 수 있습니다.");
		}
		StudentProfile profile = studentProfileRepository.findById(userId)
				.orElseThrow(StudentProfileNotFoundException::new);
		List<StudentProfileResponse.Career> careers = studentCareerRepository
				.findAllByUserIdOrderByStartMonthDescIdDesc(userId).stream()
				.map(career -> new StudentProfileResponse.Career(career.getId(), career.getCareerName(),
						career.getStartMonth(), career.getEndMonth(), career.getDescription()))
				.toList();
		String imageUrl = profile.getProfileImageKey() == null ? null
				: storageClient.createSignedUrl(profile.getProfileImageKey());
		return new StudentProfileResponse(userId, user.getName(), user.getEmail(), formatPhoneNumber(user.getPhoneNumber()),
				profile.getBirthDate(), profile.getNationality(), profile.getCurrentRegion(), profile.getKoreanLevel(),
				profile.getVisaCode(), profile.getUniversityName(), profile.getEnrollmentStartDate(), profile.getStudyProgramId(),
				studyProgram(profile), profile.getGradeLevel(), profile.getAcademicState(), profile.getTopikLevel(), profile.getStayStartDate(),
				profile.getStayExpirationDate(), imageUrl, profile.getHirelinkBadgeIssuedAt(), careers);
	}

	@Transactional
	public StudentProfileCreateResponse createProfile(Long userId, StudentProfileCreateRequest request) {
		// 같은 회원의 등록 요청을 순서대로 처리하여 중복 생성을 방지한다.
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 프로필을 등록할 수 있습니다.");
		}
		if (!user.isPhoneVerified()) {
			throw new PhoneNotVerifiedException();
		}
		if (studentProfileRepository.existsById(userId)) {
			throw new DuplicateStudentProfileException();
		}
		if (request.birthDate().isAfter(LocalDate.now(KOREA_ZONE).minusYears(18))) {
			throw new InvalidStudentProfileException("만 18세 이상만 프로필을 등록할 수 있습니다.");
		}
		if (!COUNTRY_CODES.contains(request.nationality())) {
			throw new InvalidStudentProfileException("올바른 국적 코드를 입력해주세요.");
		}

		user.updateName(request.name());
		StudentProfile profile = new StudentProfile(user, request.birthDate(), request.nationality(),
				request.currentRegion(), KoreanLevel.valueOf(request.koreanLevel()));
		studentProfileRepository.saveAndFlush(profile);

		return new StudentProfileCreateResponse(profile.getId(), user.getName(), formatPhoneNumber(user.getPhoneNumber()),
				profile.getBirthDate(), profile.getNationality(), profile.getCurrentRegion(), profile.getKoreanLevel());
	}

	@Transactional
	public StudentProfileUpdateResponse updateProfile(Long userId, StudentProfileUpdateRequest request) {
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 프로필을 수정할 수 있습니다.");
		}
		StudentProfile profile = studentProfileRepository.findById(userId)
				.orElseThrow(StudentProfileNotFoundException::new);
		if (!request.hasChanges()) {
			throw new InvalidStudentProfileException("수정할 항목을 하나 이상 입력해주세요.");
		}
		if (request.getBirthDate() != null
				&& request.getBirthDate().isAfter(LocalDate.now(KOREA_ZONE).minusYears(18))) {
			throw new InvalidStudentProfileException("만 18세 이상만 프로필을 수정할 수 있습니다.");
		}
		if (request.getNationality() != null && !COUNTRY_CODES.contains(request.getNationality())) {
			throw new InvalidStudentProfileException("올바른 국적 코드를 입력해주세요.");
		}
		String visaCode = request.getVisaCode() != null ? request.getVisaCode() : profile.getVisaCode();
		if (request.getVisaCode() != null
				&& (!StudyProgramType.supportsDetailedVisa(visaCode) || !visaTypeRepository.existsById(visaCode))) {
			throw new InvalidStudentProfileException("지원하는 체류자격은 D-2-1·D-2-2·D-2-3·D-2-4·D-2-6입니다.");
		}
		if (request.getVisaCode() != null) {
			if (verifications.existsById(userId)) {
				throw new InvalidStudentProfileException("등록증 인증 회원의 체류자격은 직접 수정할 수 없습니다.");
			}
			StudyProgramType program = studyProgram(profile);
			if (profile.getStudyProgramId() != null && (program == null || !program.getVisaCode().equals(visaCode))) {
				throw new InvalidStudentProfileException("저장된 학업과정과 체류자격이 일치하지 않습니다.");
			}
		}
		LocalDate expirationDate = request.getStayExpirationDate() != null
				? request.getStayExpirationDate() : profile.getStayExpirationDate();
		if (request.getStayExpirationDate() != null && profile.getStayStartDate() != null
				&& expirationDate.isBefore(profile.getStayStartDate())) {
			throw new InvalidStudentProfileException("체류 만료일은 체류 시작일보다 빠를 수 없습니다.");
		}

		if (request.getName() != null) {
			user.updateName(request.getName());
		}
		profile.updateBasicInformation(
				request.getBirthDate() != null ? request.getBirthDate() : profile.getBirthDate(),
				request.getNationality() != null ? request.getNationality() : profile.getNationality(),
				request.getCurrentRegion() != null ? request.getCurrentRegion() : profile.getCurrentRegion(),
				request.getKoreanLevel() != null ? KoreanLevel.valueOf(request.getKoreanLevel()) : profile.getKoreanLevel());
		Short topikLevel = request.hasTopikLevel()
				? (request.getTopikLevel() == null ? null : request.getTopikLevel().shortValue()) : profile.getTopikLevel();
		profile.updateOnboardingInformation(visaCode,
				profile.getUniversityName(), profile.getAcademicState(), topikLevel, expirationDate);

		return new StudentProfileUpdateResponse(userId, user.getName(), user.getEmail(), formatPhoneNumber(user.getPhoneNumber()),
				profile.getBirthDate(), profile.getNationality(), profile.getCurrentRegion(), profile.getKoreanLevel(),
				profile.getVisaCode(), profile.getUniversityName(), profile.getStudyProgramId(), studyProgram(profile), profile.getGradeLevel(),
				profile.getAcademicState(), profile.getTopikLevel(), profile.getStayExpirationDate(), profile.getHirelinkBadgeIssuedAt());
	}

	private StudyProgramType studyProgram(StudentProfile profile) {
		return profile.getStudyProgramId() == null ? null : studyPrograms.findById(profile.getStudyProgramId())
				.map(program -> StudyProgramType.fromProgramName(program.getProgramName())).orElse(null);
	}

	private String formatPhoneNumber(String phoneNumber) {
		return phoneNumber.substring(0, 3) + "-" + phoneNumber.substring(3, 7) + "-" + phoneNumber.substring(7);
	}
}
