package com.hirelink.domain.employer.dto;

public record EmployerProfileResponse(Long employerId, String name, String email, String phoneNumber, String position) {
}
