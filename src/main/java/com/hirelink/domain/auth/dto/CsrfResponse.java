package com.hirelink.domain.auth.dto;

public record CsrfResponse(String headerName, String token) {
}
