package com.hirelink.domain.auth.dto;

import java.time.Instant;

public record PhoneVerificationConfirmResponse(String phoneNumber, Instant verifiedAt, String message) {
}
