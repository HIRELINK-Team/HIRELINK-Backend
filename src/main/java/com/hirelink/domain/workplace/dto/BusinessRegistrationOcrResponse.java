package com.hirelink.domain.workplace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "등록증에서 인식한 사업자번호. workplaceAddress는 이전 클라이언트 호환 필드이며 새 OCR은 주소를 추출하지 않는다.")
public record BusinessRegistrationOcrResponse(
		@Schema(description = "사업자등록번호. 입력은 숫자 10자리 또는 XXX-XX-XXXXX, 응답은 하이픈 포함",
				example = "123-45-67890",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String businessRegistrationNumber,
		@Schema(description = "이전 응답 호환 필드. 새 OCR은 주소를 추출하지 않아 null이며 주소 검색으로 별도 입력", maxLength = 500,
				example = "서울특별시 성동구 예시로 10",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String workplaceAddress) {
}
