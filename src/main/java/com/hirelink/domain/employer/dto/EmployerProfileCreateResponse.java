package com.hirelink.domain.employer.dto;

public record EmployerProfileCreateResponse(Long employerId, String name, String email,
		String phoneNumber, String acquisitionChannel, String position) {
}
