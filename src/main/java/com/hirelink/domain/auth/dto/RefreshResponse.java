package com.hirelink.domain.auth.dto;

public record RefreshResponse(String accessToken, String tokenType, long expiresIn) {
}
