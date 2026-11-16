package com.hirelink.domain.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유학생 경력")
public record StudentCareerResponse(
		@Schema(description = "경력 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long careerId,
		@Schema(description = "경력명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "카페 아르바이트", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String careerName,
		@Schema(description = "근무 시작월 (YYYY-MM). 한국 기준 이번 달까지 허용",
				pattern = "[0-9]{4}-(0[1-9]|1[0-2])",
				example = "2026-01", type = "string",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String startMonth,
		@Schema(description = "근무 종료월 (YYYY-MM). 시작월 이상, 한국 기준 이번 달까지 허용",
				pattern = "[0-9]{4}-(0[1-9]|1[0-2])",
				example = "2026-06", type = "string",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String endMonth,
		@Schema(description = "근무 내용. null 또는 빈 문자열 허용", example = "음료 제조 및 고객 응대", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String description) {
}
