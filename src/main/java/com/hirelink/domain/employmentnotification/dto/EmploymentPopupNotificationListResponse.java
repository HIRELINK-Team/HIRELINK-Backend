package com.hirelink.domain.employmentnotification.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmploymentPopupNotificationListResponse(
		@Schema(description = "오래된 순서의 미확인 채용 팝업 알림. 없으면 빈 배열")
		List<EmploymentPopupNotificationItemResponse> notifications) {
}
