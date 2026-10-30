package com.hirelink.domain.student.service;

import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.dto.StudentCareerCreateRequest;
import com.hirelink.domain.student.dto.StudentCareerResponse;
import com.hirelink.domain.student.dto.StudentCareerUpdateRequest;
import com.hirelink.domain.student.entity.StudentCareer;
import com.hirelink.domain.student.repository.StudentCareerRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.global.exception.InvalidStudentCareerException;
import com.hirelink.global.exception.StudentCareerNotFoundException;
import com.hirelink.global.exception.StudentProfileNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class StudentCareerService {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final UserAccountRepository userAccountRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final StudentCareerRepository studentCareerRepository;

	public StudentCareerService(UserAccountRepository userAccountRepository,
			StudentProfileRepository studentProfileRepository, StudentCareerRepository studentCareerRepository) {
		this.userAccountRepository = userAccountRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.studentCareerRepository = studentCareerRepository;
	}

	@Transactional
	public StudentCareerResponse createCareer(Long userId, StudentCareerCreateRequest request) {
		validateStudent(userId);
		if (!studentProfileRepository.existsById(userId)) {
			throw new StudentProfileNotFoundException();
		}

		YearMonth startMonth = request.getStartMonth();
		YearMonth endMonth = request.getEndMonth();
		validatePeriod(startMonth, endMonth);

		StudentCareer career = new StudentCareer(userId, request.getCareerName(), startMonth.toString(),
				endMonth.toString(), request.getDescription());
		studentCareerRepository.save(career);
		return new StudentCareerResponse(career.getId(), career.getCareerName(), career.getStartMonth(),
				career.getEndMonth(), career.getDescription());
	}

	@Transactional
	public StudentCareerResponse updateCareer(Long userId, Long careerId, StudentCareerUpdateRequest request) {
		validateStudent(userId);
		if (!request.hasChanges()) {
			throw new InvalidStudentCareerException("수정할 항목을 입력해주세요.");
		}

		StudentCareer career = studentCareerRepository.findByIdForUpdate(careerId)
				.orElseThrow(StudentCareerNotFoundException::new);
		if (!career.getUserId().equals(userId)) {
			throw new AccessDeniedException("본인의 경력만 수정할 수 있습니다.");
		}

		String careerName = request.getCareerName() != null ? request.getCareerName() : career.getCareerName();
		YearMonth startMonth = request.getStartMonth() != null
				? request.getStartMonth() : YearMonth.parse(career.getStartMonth());
		YearMonth endMonth = request.getEndMonth() != null
				? request.getEndMonth() : YearMonth.parse(career.getEndMonth());
		String description = request.hasDescription() ? request.getDescription() : career.getDescription();
		validatePeriod(startMonth, endMonth);

		career.updateInformation(careerName, startMonth.toString(), endMonth.toString(), description);
		return new StudentCareerResponse(career.getId(), career.getCareerName(), career.getStartMonth(),
				career.getEndMonth(), career.getDescription());
	}

	private void validateStudent(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 경력을 관리할 수 있습니다.");
		}
	}

	private void validatePeriod(YearMonth startMonth, YearMonth endMonth) {
		if (endMonth.isBefore(startMonth)) {
			throw new InvalidStudentCareerException("근무 종료 월은 시작 월보다 빠를 수 없습니다.");
		}
		YearMonth currentMonth = YearMonth.now(KOREA_ZONE);
		if (startMonth.isAfter(currentMonth) || endMonth.isAfter(currentMonth)) {
			throw new InvalidStudentCareerException("근무 시작 월과 종료 월은 이번 달까지만 입력할 수 있습니다.");
		}
	}
}
