package com.hirelink.domain.workplace.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 사업장 목록")
public record WorkplaceListResponse(
		@Schema(description = "본인 사업장 목록. ID 오름차순, 없으면 []", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<WorkplaceSummary> workplaces) {

	@Schema(name = "WorkplaceListResponseWorkplaceSummary")
	public record WorkplaceSummary(
			@Schema(description = "사업장 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long workplaceId,
			@Schema(description = "사업장명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "예시카페", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String workplaceName,
			@Schema(description = "업종 ID: 1 음식점·카페, 2 매장·판매, 3 사무·행사·관광, 4 통·번역, 5 면세점, 6 전공·학업, 7 인턴십, 8 기타",
					example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long businessTypeId,
			@Schema(description = "업종 코드 (한글 표시명이 아님)", example = "FOOD_CAFE", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String businessTypeName,
			@Schema(description = "사업장 주소", maxLength = 500, example = "서울특별시 성동구 예시로 10", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String workplaceAddress,
			@Schema(description = "직원 수 구간",
					allowableValues = {"UNDER_5", "FIVE_OR_MORE"},
					example = "UNDER_5",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String employeeCountRange) {
	}
}
