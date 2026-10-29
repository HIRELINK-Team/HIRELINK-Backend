package com.hirelink.domain.auth.dto;

public record PhoneVerificationRequestResponse(long expiresIn, String message) {
}
