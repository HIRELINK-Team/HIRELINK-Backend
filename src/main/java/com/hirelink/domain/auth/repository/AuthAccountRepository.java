package com.hirelink.domain.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.auth.entity.AuthAccount;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {
}
