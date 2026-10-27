package com.hirelink.domain.auth.dto;

import com.hirelink.domain.member.entity.MemberRole;

public record SignupResponse(Long userId, String email, MemberRole role) {
}
