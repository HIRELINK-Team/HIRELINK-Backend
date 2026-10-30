package com.hirelink.domain.workplace.dto;

import java.util.List;

public record WorkplaceListResponse(List<WorkplaceSummary> workplaces) {

	public record WorkplaceSummary(
			Long workplaceId,
			String workplaceName,
			Long businessTypeId,
			String businessTypeName,
			String workplaceAddress,
			String employeeCountRange) {
	}
}
