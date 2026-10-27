package com.hirelink.domain.auth.service;

import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.dto.SignupRequest;
import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.dto.LoginRequest;
import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.DuplicateEmailException;
import com.hirelink.global.exception.InvalidPasswordException;
import com.hirelink.global.exception.InvalidCredentialsException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class AuthService {

	private final UserAccountRepository userAccountRepository;
	private final AuthAccountRepository authAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final String dummyPasswordHash;

	public AuthService(UserAccountRepository userAccountRepository,
			AuthAccountRepository authAccountRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.userAccountRepository = userAccountRepository;
		this.authAccountRepository = authAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public AuthResult signup(SignupRequest request) {
		String email = request.email();
		if (userAccountRepository.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}

		String passwordHash;
		try {
			passwordHash = passwordEncoder.encode(request.password());
		} catch (IllegalArgumentException exception) {
			// 인코더가 거부한 입력을 서버 장애로 처리하거나 비밀번호를 임의로 잘라 저장하지 않는다.
			throw new InvalidPasswordException();
		}

		UserAccount userAccount = new UserAccount(email, MemberRole.valueOf(request.role()));
		try {
			userAccountRepository.saveAndFlush(userAccount);
			AuthAccount authAccount = new AuthAccount(userAccount, AuthProvider.LOCAL, email, passwordHash);
			authAccountRepository.saveAndFlush(authAccount);
		} catch (DataIntegrityViolationException exception) {
			// 사전 조회를 동시에 통과한 가입 요청도 DB UNIQUE 제약으로 차단한다.
			if (isDuplicateEmail(exception)) {
				throw new DuplicateEmailException();
			}
			throw exception;
		}

		return tokenService.issueTokens(userAccount);
	}

	@Transactional
	public AuthResult login(LoginRequest request) {
		AuthAccount authAccount = authAccountRepository
				.findByProviderAndProviderUserId(AuthProvider.LOCAL, request.email()).orElse(null);
		// 존재하지 않는 이메일도 해시 비교를 수행해 응답 시간 차이를 줄인다.
		String passwordHash = authAccount == null ? dummyPasswordHash : authAccount.getPasswordHash();
		boolean passwordMatches;
		try {
			passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
		} catch (IllegalArgumentException exception) {
			passwordMatches = false;
		}
		if (authAccount == null || !passwordMatches) {
			throw new InvalidCredentialsException();
		}

		UserAccount userAccount = authAccount.getUserAccount();
		if (userAccount.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		return tokenService.issueTokens(userAccount);
	}

	private boolean isDuplicateEmail(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				return "uk_user_account_email".equals(constraintName)
						|| "uk_auth_account_provider_user_id".equals(constraintName);
			}
		}
		return false;
	}
}
