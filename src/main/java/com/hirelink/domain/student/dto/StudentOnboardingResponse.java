package com.hirelink.domain.student.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentOnboardingResponse(
		@Schema(description = "유학생 회원 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long studentId,
		@Schema(description = "온보딩 완료 여부. 추천 이용 자격이나 현재 체류기간 유효 여부를 뜻하지 않는다", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean completed,
		@Schema(description = "전체 저장과 함께 기록한 최초 완료 시각", requiredMode = Schema.RequiredMode.REQUIRED) Instant completedAt) {
}
