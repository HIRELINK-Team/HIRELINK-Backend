package com.hirelink.domain.member.service;

import java.time.Instant;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.dto.OnboardingStatusResponse;
import com.hirelink.domain.member.dto.OnboardingStatusResponse.NextStep;
import com.hirelink.domain.member.dto.OnboardingStatusResponse.Status;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.student.entity.StudentProfile;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.repository.StudentRegistrationVerificationRepository;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class OnboardingStatusService {

	private final UserAccountRepository users;
	private final StudentProfileRepository profiles;
	private final StudentRegistrationVerificationRepository verifications;

	public OnboardingStatusService(UserAccountRepository users, StudentProfileRepository profiles,
			StudentRegistrationVerificationRepository verifications) {
		this.users = users;
		this.profiles = profiles;
		this.verifications = verifications;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public OnboardingStatusResponse getStatus(Long userId) {
		UserAccount user = users.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) { throw new WithdrawnAccountException(); }
		var verification = user.getRole() == MemberRole.STUDENT ? verifications.findById(userId).orElse(null) : null;
		if (verification == null) {
			return new OnboardingStatusResponse(userId, user.getRole(), Status.NOT_APPLICABLE, NextStep.EXISTING_FLOW, null);
		}
		Instant completedAt = profiles.findById(userId).map(StudentProfile::getOnboardingCompletedAt).orElse(null);
		NextStep nextStep = verification.getBirthDate() == null ? NextStep.BASIC_INFORMATION : NextStep.CURRENT_REGION;
		return completedAt == null
				? new OnboardingStatusResponse(userId, user.getRole(), Status.REQUIRED, nextStep, null)
				: new OnboardingStatusResponse(userId, user.getRole(), Status.COMPLETED, NextStep.NONE, completedAt);
	}
}
