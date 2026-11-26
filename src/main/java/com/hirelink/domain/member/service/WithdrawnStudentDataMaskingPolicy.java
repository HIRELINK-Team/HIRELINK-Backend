package com.hirelink.domain.member.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;

@Component
public class WithdrawnStudentDataMaskingPolicy {

	private static final Duration RECOVERY_PERIOD = Duration.ofDays(30);

	private final Clock clock;

	public WithdrawnStudentDataMaskingPolicy(Clock clock) {
		this.clock = clock;
	}

	public boolean shouldMask(UserAccount student) {
		Instant requestedAt = student.getWithdrawalRequestedAt();
		return student.getRole() == MemberRole.STUDENT && requestedAt != null
				&& !Instant.now(clock).isBefore(requestedAt.plus(RECOVERY_PERIOD));
	}
}
