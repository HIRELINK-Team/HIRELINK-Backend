package com.hirelink.domain.employmentnotification.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmploymentPopupNotificationConfirmResponse(
		@Schema(description = "확인한 팝업 알림 ID", example = "401")
		Long notificationId,
		@Schema(description = "최초 확인 시각", example = "2026-11-20T05:05:00Z")
		Instant confirmedAt) {
}
