package com.hirelink.domain.employer.dto;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "사업주 온보딩 상태. 미완료이면 항상 이름·유입 경로부터 다시 입력한다.")
public record EmployerOnboardingStatusResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long userId,
        @Schema(allowableValues = {"REQUIRED", "COMPLETED"}, requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(allowableValues = {"BASIC_INFORMATION", "DASHBOARD"}, requiredMode = Schema.RequiredMode.REQUIRED) String nextStep,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant completedAt) {}
