package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegistrationRenewalFrontResponse(
    @Schema(description="회원별 최신 갱신 식별자. 재촬영하면 이전 값은 무효") UUID renewalId,
    String name, String nationalityCode,
    @Schema(description="등록증 원문 체류자격. D-10 등도 갱신 가능하며 D-2 세부 코드는 학업과정으로 조합한다") String visaCode,
    String registrationNumber, LocalDate issueDate,
    @Schema(description="갱신 완료 제한 시각. 시작 후 30분") Instant renewalExpiresAt) { }
