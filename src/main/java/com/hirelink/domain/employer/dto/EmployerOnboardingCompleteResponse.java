package com.hirelink.domain.employer.dto;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "사업주 프로필·사업장 일괄 저장 완료")
public record EmployerOnboardingCompleteResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long workplaceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant completedAt) {}
