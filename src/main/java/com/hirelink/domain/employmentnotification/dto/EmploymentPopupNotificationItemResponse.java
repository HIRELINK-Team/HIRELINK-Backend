package com.hirelink.domain.employmentnotification.dto;

import java.time.Instant;

import com.hirelink.domain.employmentnotification.entity.EmploymentPopupNotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmploymentPopupNotificationItemResponse(
		@Schema(description = "팝업 알림 ID", example = "401")
		Long notificationId,
		@Schema(description = "채용 팝업 알림 유형", example = "OFFER_CREATED")
		EmploymentPopupNotificationType type,
		@Schema(description = "팝업 표시 문구", example = "새로운 채용 제안이 도착했습니다. 서비스에서 응답해 주세요.")
		String message,
		@Schema(description = "관련 지원 ID", example = "201")
		Long applicationId,
		@Schema(description = "관련 채용 제안 ID. 지원 생성·거절 알림은 null", example = "301", nullable = true)
		Long employmentOfferId,
		@Schema(description = "알림 생성 시각", example = "2026-11-20T05:00:00Z")
		Instant createdAt) {
}
