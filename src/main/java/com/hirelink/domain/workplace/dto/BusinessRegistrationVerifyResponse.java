package com.hirelink.domain.workplace.dto;

public record BusinessRegistrationVerifyResponse(String businessRegistrationNumber, boolean valid, String message) {
}
