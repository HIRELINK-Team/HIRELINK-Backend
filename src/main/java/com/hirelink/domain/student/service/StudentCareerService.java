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
import com.hirelink.domain.student.entity.StudentCareer;
import com.hirelink.domain.student.repository.StudentCareerRepository;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.global.exception.InvalidStudentCareerException;
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
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 경력을 등록할 수 있습니다.");
		}
		if (!studentProfileRepository.existsById(userId)) {
			throw new StudentProfileNotFoundException();
		}

		YearMonth startMonth = request.getStartMonth();
		YearMonth endMonth = request.getEndMonth();
		if (endMonth.isBefore(startMonth)) {
			throw new InvalidStudentCareerException("근무 종료 월은 시작 월보다 빠를 수 없습니다.");
		}
		YearMonth currentMonth = YearMonth.now(KOREA_ZONE);
		if (startMonth.isAfter(currentMonth) || endMonth.isAfter(currentMonth)) {
			throw new InvalidStudentCareerException("근무 시작 월과 종료 월은 이번 달까지만 입력할 수 있습니다.");
		}

		StudentCareer career = new StudentCareer(userId, request.getCareerName(), startMonth.toString(),
				endMonth.toString(), request.getDescription());
		studentCareerRepository.save(career);
		return new StudentCareerResponse(career.getId(), career.getCareerName(), career.getStartMonth(),
				career.getEndMonth(), career.getDescription());
	}
}
