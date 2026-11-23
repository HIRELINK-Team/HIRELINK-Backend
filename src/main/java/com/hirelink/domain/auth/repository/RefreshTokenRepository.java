package com.hirelink.domain.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
	void deleteAllByUserAccountId(Long userId);
	Optional<RefreshToken> findByTokenHash(String tokenHash);
	boolean existsBySessionIdAndUserAccountIdAndExpiresAtAfter(UUID sessionId, Long userId, Instant now);

	@Modifying
	@Query("delete from RefreshToken token where token.tokenHash = :tokenHash")
	int deleteByTokenHash(@Param("tokenHash") String tokenHash);
}
