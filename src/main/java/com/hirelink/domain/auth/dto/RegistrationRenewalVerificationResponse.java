package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegistrationRenewalVerificationResponse(UUID renewalId, String serialNumber,
    @Schema(description="아직 최종 반영하지 않은 만료일") LocalDate stayExpiresOn,
    boolean cardVerified, boolean documentVerified, boolean requiresStayDocument, Instant renewalExpiresAt) { }
