package com.hirelink.domain.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hirelink.domain.auth.dto.LoginRequest;
import com.hirelink.domain.auth.dto.SignupRequest;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.service.AuthService;
import com.hirelink.domain.auth.service.TokenService;
import com.hirelink.domain.member.repository.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthServiceDefaultConfigurationTest {

	@Test
	void missingLocalAuthSettingRejectsSignupAndLoginBeforeAccountOrTokenAccess() {
		UserAccountRepository users = mock(UserAccountRepository.class);
		AuthAccountRepository accounts = mock(AuthAccountRepository.class);
		TokenService tokens = mock(TokenService.class);
		new ApplicationContextRunner()
				.withBean(UserAccountRepository.class, () -> users)
				.withBean(AuthAccountRepository.class, () -> accounts)
				.withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
				.withBean(TokenService.class, () -> tokens)
				.withBean(AuthService.class)
				.run(context -> {
					AuthService service = context.getBean(AuthService.class);
					assertThatThrownBy(() -> service.signup(new SignupRequest("member@example.com", "password123", "STUDENT")))
							.isInstanceOf(AccessDeniedException.class);
					assertThatThrownBy(() -> service.login(new LoginRequest("member@example.com", "password123")))
							.isInstanceOf(AccessDeniedException.class);
					verifyNoInteractions(users, accounts, tokens);
				});
	}
}
