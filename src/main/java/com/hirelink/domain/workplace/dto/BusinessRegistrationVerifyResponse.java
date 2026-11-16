package com.hirelink.domain.workplace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사업자등록번호 영업 상태 확인 결과")
public record BusinessRegistrationVerifyResponse(
		@Schema(description = "사업자등록번호. 입력은 숫자 10자리 또는 XXX-XX-XXXXX, 응답은 하이픈 포함",
				example = "123-45-67890",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String businessRegistrationNumber,
		@Schema(description = "국세청 조회 결과 현재 영업 중인 사업자인지 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		boolean valid,
		@Schema(description = "처리 결과 메시지", example = "처리가 완료되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String message) {
}
