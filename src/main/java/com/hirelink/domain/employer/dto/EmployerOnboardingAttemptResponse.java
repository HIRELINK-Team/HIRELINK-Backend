package com.hirelink.domain.employer.dto;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "새 온보딩 시도. 이전 시도의 OCR·사업자 인증은 무효화된다. 계정·전화번호·약관은 유지한다.")
public record EmployerOnboardingAttemptResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID attemptId,
        @Schema(description = "시도 유효기간 30분. 회원 계정 만료가 아님", requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {}
