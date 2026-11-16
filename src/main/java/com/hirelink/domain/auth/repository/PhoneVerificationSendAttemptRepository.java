package com.hirelink.domain.auth.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.auth.entity.PhoneVerificationSendAttempt;

public interface PhoneVerificationSendAttemptRepository extends JpaRepository<PhoneVerificationSendAttempt, Long> {

	// 아직 저장된 인증 정보가 없는 번호도 트랜잭션 종료까지 잠근다. 1은 휴대전화 인증용 잠금 구분값이다.
	@Query(value = "select true from pg_advisory_xact_lock(1, cast(:phoneNumber as integer))", nativeQuery = true)
	boolean lockPhoneNumber(@Param("phoneNumber") String phoneNumber);

	long countByUserIdAndRequestedAtAfter(Long userId, Instant since);

	long countByPhoneNumberAndRequestedAtAfter(String phoneNumber, Instant since);

	long countBySignupIdentityAndRequestedAtAfter(String identity, Instant since);

	@Modifying
	@Query("update PhoneVerificationSendAttempt attempt set attempt.userId = :userId, attempt.signupIdentity = null "
			+ "where attempt.signupIdentity = :identity")
	void transferSignupAttempts(@Param("identity") String identity, @Param("userId") Long userId);

	@Modifying
	@Query("delete from PhoneVerificationSendAttempt attempt where attempt.requestedAt <= :before "
			+ "and (attempt.signupIdentity = :identity or attempt.phoneNumber = :phoneNumber)")
	void deleteExpiredSignupAttempts(@Param("identity") String identity, @Param("phoneNumber") String phoneNumber,
			@Param("before") Instant before);

	@Modifying
	@Query("delete from PhoneVerificationSendAttempt attempt where attempt.requestedAt <= :before "
			+ "and (attempt.userId = :userId or attempt.phoneNumber = :phoneNumber)")
	void deleteExpiredAttempts(@Param("userId") Long userId, @Param("phoneNumber") String phoneNumber,
			@Param("before") Instant before);
}
