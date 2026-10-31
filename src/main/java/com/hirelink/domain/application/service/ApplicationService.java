package com.hirelink.domain.application.service;

import java.time.Instant;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.application.dto.ApplicationWithdrawResponse;
import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.ApplicationNotFoundException;
import com.hirelink.global.exception.ApplicationStateConflictException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class ApplicationService {

	private final UserAccountRepository userAccountRepository;
	private final ApplicationRepository applicationRepository;

	public ApplicationService(UserAccountRepository userAccountRepository,
			ApplicationRepository applicationRepository) {
		this.userAccountRepository = userAccountRepository;
		this.applicationRepository = applicationRepository;
	}

	@Transactional
	public ApplicationWithdrawResponse withdrawApplication(Long userId, Long applicationId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 지원을 취소할 수 있습니다.");
		}

		Application application = applicationRepository.findByIdForUpdate(applicationId)
				.orElseThrow(ApplicationNotFoundException::new);
		if (!application.getStudentProfile().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 지원만 취소할 수 있습니다.");
		}

		Instant endedAt = Instant.now();
		if (!application.withdraw(endedAt)) {
			throw new ApplicationStateConflictException();
		}
		applicationRepository.flush();
		return new ApplicationWithdrawResponse(application.getId(), application.getStatus(),
				application.getEndReason(), application.getEndedAt());
	}
}
