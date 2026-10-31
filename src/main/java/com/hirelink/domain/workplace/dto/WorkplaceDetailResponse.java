package com.hirelink.domain.workplace.dto;

import java.math.BigDecimal;

public record WorkplaceDetailResponse(
		Long workplaceId,
		String workplaceName,
		String businessRegistrationNumber,
		Long businessTypeId,
		String businessTypeName,
		String employeeCountRange,
		String workplaceAddress,
		BigDecimal latitude,
		BigDecimal longitude) {
}
