package com.hirelink.domain.auth.dto;

import com.hirelink.domain.member.entity.MemberRole;

public record LoginResponse(Long userId, MemberRole role, String accessToken, String tokenType, long expiresIn) {
}
