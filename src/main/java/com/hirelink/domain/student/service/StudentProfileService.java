package com.hirelink.domain.student.service;

import java.time.LocalDate;
import java.time.ZoneId;
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
import com.hirelink.domain.student.entity.KoreanLevel;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.global.exception.DuplicateStudentProfileException;
import com.hirelink.global.exception.InvalidStudentProfileException;
import com.hirelink.global.exception.PhoneNotVerifiedException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentProfileService {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

	private final UserAccountRepository userAccountRepository;
	private final StudentProfileRepository studentProfileRepository;

	public StudentProfileService(UserAccountRepository userAccountRepository,
			StudentProfileRepository studentProfileRepository) {
		this.userAccountRepository = userAccountRepository;
		this.studentProfileRepository = studentProfileRepository;
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

		String phoneNumber = user.getPhoneNumber();
		String formattedPhoneNumber = phoneNumber.substring(0, 3) + "-" + phoneNumber.substring(3, 7)
				+ "-" + phoneNumber.substring(7);
		return new StudentProfileCreateResponse(profile.getId(), user.getName(), formattedPhoneNumber,
				profile.getBirthDate(), profile.getNationality(), profile.getCurrentRegion(), profile.getKoreanLevel());
	}
}
