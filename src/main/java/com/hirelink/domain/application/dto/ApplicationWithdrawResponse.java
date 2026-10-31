package com.hirelink.domain.application.dto;

import java.time.Instant;

import com.hirelink.domain.application.entity.ApplicationEndReason;
import com.hirelink.domain.application.entity.ApplicationStatus;

public record ApplicationWithdrawResponse(Long applicationId, ApplicationStatus status,
		ApplicationEndReason endReason, Instant endedAt) {
}
