package com.hirelink.domain.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.member.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
	boolean existsByEmail(String email);
}
