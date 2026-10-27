package com.hirelink.domain.auth.dto;

import com.hirelink.domain.member.entity.MemberRole;

// 서비스와 컨트롤러 사이에서만 사용하며 응답 본문에는 직접 반환하지 않는다.
public record AuthResult(Long userId, String email, MemberRole role, String accessToken, String refreshToken) {
	@Override
	public String toString() {
		return "AuthResult[userId=" + userId + ", role=" + role + "]";
	}
}
