package com.hirelink.domain.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
