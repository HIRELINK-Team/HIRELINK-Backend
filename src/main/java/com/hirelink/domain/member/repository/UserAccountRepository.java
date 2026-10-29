package com.hirelink.domain.member.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.member.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from UserAccount user where user.id = :id")
	Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
	boolean existsByEmail(String email);
}
