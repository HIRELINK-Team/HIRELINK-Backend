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

	// 가입 정보가 아직 없는 동일 소셜 사용자도 같은 순서로 잠근다.
	@Query(value = "select true from pg_advisory_xact_lock(2, hashtext(:identity))", nativeQuery = true)
	boolean lockIdentity(@Param("identity") String identity);

	@Modifying
	@Query(value = "delete from social_auth_session where provider = :provider and provider_user_id = :subject", nativeQuery = true)
	void deleteByIdentity(@Param("provider") String provider, @Param("subject") String subject);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from SocialAuthSession session where session.tokenHash = :hash")
	Optional<SocialAuthSession> findByTokenHashForUpdate(@Param("hash") String hash);

	@Modifying
	@Query("delete from SocialAuthSession session where session.expiresAt <= :now")
	int deleteExpired(@Param("now") Instant now);
}
