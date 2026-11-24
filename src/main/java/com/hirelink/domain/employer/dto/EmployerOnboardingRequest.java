package com.hirelink.domain.employer.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.hirelink.domain.workplace.dto.WorkplaceCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "사업주 온보딩 최종 제출. 이름·유입 경로와 사업장 전체 입력을 함께 저장한다. 전화번호·약관은 기존 계정의 인증값을 유지한다.")
public record EmployerOnboardingRequest(
        @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EmployerProfileCreateRequest profile,
        @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkplaceCreateRequest workplace) {}
