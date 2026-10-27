package com.hirelink.domain.auth.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.dto.SignupRequest;
import com.hirelink.domain.auth.dto.SignupResponse;
import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.DuplicateEmailException;
import com.hirelink.global.exception.InvalidPasswordException;

@Service
public class AuthService {

	private final UserAccountRepository userAccountRepository;
	private final AuthAccountRepository authAccountRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserAccountRepository userAccountRepository,
			AuthAccountRepository authAccountRepository, PasswordEncoder passwordEncoder) {
		this.userAccountRepository = userAccountRepository;
		this.authAccountRepository = authAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
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

		return new SignupResponse(userAccount.getId(), userAccount.getEmail(), userAccount.getRole());
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
