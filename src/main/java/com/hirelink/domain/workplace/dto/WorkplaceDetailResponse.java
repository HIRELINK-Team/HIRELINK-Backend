package com.hirelink.domain.workplace.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사업장 상세 정보")
public record WorkplaceDetailResponse(
		@Schema(description = "사업장 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long workplaceId,
		@Schema(description = "사업장명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "예시카페", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String workplaceName,
		@Schema(description = "사업자등록번호. 입력은 숫자 10자리 또는 XXX-XX-XXXXX, 응답은 하이픈 포함",
				example = "123-45-67890",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String businessRegistrationNumber,
		@Schema(description = "업종 ID: 1 음식점·카페, 2 매장·판매, 3 사무·행사·관광, 4 통·번역, 5 면세점, 6 전공·학업, 7 인턴십, 8 기타",
				example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long businessTypeId,
		@Schema(description = "업종 코드 (한글 표시명이 아님)", example = "FOOD_CAFE", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String businessTypeName,
		@Schema(description = "직원 수 구간",
				allowableValues = {"UNDER_5", "FIVE_OR_MORE"},
				example = "UNDER_5",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String employeeCountRange,
		@Schema(description = "기본 주소. 분리 전 기존 데이터는 원래 전체 주소를 그대로 반환", maxLength = 500, example = "서울특별시 성동구 예시로 10", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String workplaceAddress,
		@Schema(description = "위도. 소수점 이하 최대 7자리", minimum = "-90", maximum = "90",
				example = "37.5665",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		BigDecimal latitude,
		@Schema(description = "경도. 소수점 이하 최대 7자리", minimum = "-180", maximum = "180",
				example = "126.978",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		BigDecimal longitude,
        @Schema(description = "상세 주소. 기존 데이터 또는 미입력은 null", maxLength = 500,
                requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String workplaceAddressDetail) {
}
