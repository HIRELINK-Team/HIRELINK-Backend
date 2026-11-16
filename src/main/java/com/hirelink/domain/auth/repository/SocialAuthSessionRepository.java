package com.hirelink.domain.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.auth.entity.SocialAuthSession;

import jakarta.persistence.LockModeType;

public interface SocialAuthSessionRepository extends JpaRepository<SocialAuthSession, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from SocialAuthSession session where session.tokenHash = :hash")
	Optional<SocialAuthSession> findByTokenHashForUpdate(@Param("hash") String hash);

	@Modifying
	@Query("delete from SocialAuthSession session where session.expiresAt <= :now")
	int deleteExpired(@Param("now") Instant now);
}
