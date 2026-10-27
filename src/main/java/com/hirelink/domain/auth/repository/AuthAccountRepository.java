package com.hirelink.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.AuthProvider;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {
	Optional<AuthAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
