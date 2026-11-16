package com.hirelink.domain.workplace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "등록증에서 인식한 사업자번호와 주소")
public record BusinessRegistrationOcrResponse(
		@Schema(description = "사업자등록번호. 입력은 숫자 10자리 또는 XXX-XX-XXXXX, 응답은 하이픈 포함",
				example = "123-45-67890",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String businessRegistrationNumber,
		@Schema(description = "사업장 주소. 인식하지 못하면 null, 직접 입력 가능", maxLength = 500,
				example = "서울특별시 성동구 예시로 10",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String workplaceAddress) {
}
