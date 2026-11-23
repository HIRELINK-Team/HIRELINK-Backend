package com.hirelink.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.dto.LoginResponse;
import com.hirelink.domain.auth.dto.SocialAuthResponse;
import com.hirelink.domain.auth.dto.SocialAuthResponse.Status;
import com.hirelink.domain.auth.entity.AuthProvider;
import com.hirelink.domain.auth.entity.SocialAuthSession;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.repository.SocialAuthSessionRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class SocialAuthService {

	public static final Duration SIGNUP_TTL = Duration.ofMinutes(30);
	private final SecureRandom random = new SecureRandom();
	private final SocialAuthSessionRepository sessionRepository;
	private final AuthAccountRepository authAccountRepository;
	private final TokenService tokenService;
    private final ProviderTokenProtector protector;
    private final com.hirelink.domain.member.repository.UserAccountRepository users;

	public SocialAuthService(SocialAuthSessionRepository sessionRepository,
			AuthAccountRepository authAccountRepository, TokenService tokenService, ProviderTokenProtector protector, com.hirelink.domain.member.repository.UserAccountRepository users) {
		this.sessionRepository = sessionRepository;
		this.authAccountRepository = authAccountRepository;
		this.tokenService = tokenService; this.protector=protector; this.users=users;
	}

	@Transactional
	public String createSession(AuthProvider provider, String subject, MemberRole role, String email) {
        return createSession(provider,subject,role,email,null);
    }

    @Transactional
    public String createSession(AuthProvider provider, String subject, MemberRole role, String email, String refreshToken) {
		if (provider == AuthProvider.LOCAL || subject == null || subject.isBlank() || subject.length() > 255) {
			throw new SocialAuthException(HttpStatus.UNAUTHORIZED, "SOCIAL_AUTH_FAILED", "소셜 인증을 완료하지 못했습니다.");
		}
		sessionRepository.lockIdentity(hash(provider.name() + ":" + subject));
		var account = authAccountRepository.findByProviderAndProviderUserId(provider, subject);
		if (account.isPresent()) {
			var member=users.findByIdForUpdate(account.get().getUserAccount().getId()).orElseThrow();
            checkMember(member, role);
		} else {
			// 새 소셜 인증부터 다시 가입하며 이전 인증번호와 약관도 함께 폐기한다.
			sessionRepository.deleteByIdentity(provider.name(), subject);
		}
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = Instant.now();
		var session=new SocialAuthSession(hash(token), provider, subject, role,
                email == null || email.isBlank() || email.length() > 255 ? null : email, now, now.plus(SIGNUP_TTL));
        if(protector.enabled() && role==MemberRole.STUDENT && provider==AuthProvider.GOOGLE && refreshToken!=null) {
            String encrypted=protector.encrypt(refreshToken,provider.name()+":"+subject);
            session.saveRevocationToken(encrypted); account.ifPresent(value -> value.saveRevocationToken(encrypted));
        }
        sessionRepository.save(session);
		return token;
	}

	@Transactional
	public Result getResult(String token) {
		SocialAuthSession session = findValidSession(token);
		var account = authAccountRepository.findByProviderAndProviderUserId(
				session.getProvider(), session.getProviderUserId());
		if (account.isEmpty()) {
			return new Result(new SocialAuthResponse(Status.SIGNUP_REQUIRED, session.getRole(),
					session.getEmail(), session.getExpiresAt(), null), null);
		}
		UserAccount member = users.findByIdForUpdate(account.get().getUserAccount().getId()).orElseThrow();
		checkMember(member, session.getRole());
        if(member.isWithdrawn()) {
            if(session.getCreatedAt().isBefore(member.getWithdrawalRequestedAt())) throw new WithdrawnAccountException();
            member.restoreWithdrawal();
        }
        AuthResult tokens = tokenService.issueTokens(member);
		// 기존 회원의 결과 교환은 한 번만 허용한다.
		sessionRepository.delete(session);
		return new Result(new SocialAuthResponse(Status.LOGIN, member.getRole(), member.getEmail(), null,
				new LoginResponse(member.getId(), member.getRole(), tokens.accessToken(), "Bearer",
						TokenService.ACCESS_TOKEN_TTL.toSeconds())), tokens);
	}

	SocialAuthSession requireSignupSession(String token) {
		SocialAuthSession session = findValidSession(token);
		authAccountRepository.findByProviderAndProviderUserId(session.getProvider(), session.getProviderUserId())
				.ifPresent(account -> {
					checkMember(account.getUserAccount(), session.getRole());
					throw new SocialAuthException(HttpStatus.CONFLICT, "SOCIAL_ACCOUNT_ALREADY_REGISTERED",
							"이미 가입한 소셜 계정입니다. 다시 로그인해주세요.");
				});
		return session;
	}

	private SocialAuthSession findValidSession(String token) {
		if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
			throw invalidSession();
		}
		String tokenHash = hash(token);
		SocialAuthSession identity = sessionRepository.findById(tokenHash).orElseThrow(this::invalidSession);
		// 소셜 사용자 → 가입 정보 → 전화번호 순서로 잠근다.
		sessionRepository.lockIdentity(signupIdentity(identity));
		SocialAuthSession session = sessionRepository.findByTokenHashForUpdate(tokenHash)
				.orElseThrow(this::invalidSession);
		if (!session.getExpiresAt().isAfter(Instant.now())) {
			throw invalidSession();
		}
		return session;
	}

	String signupIdentity(SocialAuthSession session) {
		return hash(session.getProvider().name() + ":" + session.getProviderUserId());
	}

	@Transactional
	public void discardSignupSession(String token) {
		if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
			return;
		}
		String tokenHash = hash(token);
		sessionRepository.findById(tokenHash).ifPresent(session -> {
			sessionRepository.lockIdentity(signupIdentity(session));
			sessionRepository.findByTokenHashForUpdate(tokenHash).ifPresent(sessionRepository::delete);
		});
	}

	@Transactional
	@Scheduled(fixedDelay = 60000, initialDelay = 60000)
	public void deleteExpiredSessions() {
		sessionRepository.deleteExpired(Instant.now());
	}

	private void checkMember(UserAccount member, MemberRole role) {
		if (member.isWithdrawn() && !member.canRestore(Instant.now())) {
			throw new WithdrawnAccountException();
		}
		if (member.getRole() != role) {
			throw new SocialAuthException(HttpStatus.FORBIDDEN, "SOCIAL_ROLE_MISMATCH", "가입한 회원 유형으로 로그인해주세요.");
		}
	}

	private SocialAuthException invalidSession() {
		return new SocialAuthException(HttpStatus.UNAUTHORIZED, "INVALID_SOCIAL_SESSION", "소셜 인증을 다시 진행해주세요.");
	}

	private String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record Result(SocialAuthResponse response, AuthResult tokens) {
		@Override
		public String toString() { return "SocialAuthResult[status=" + response.status() + "]"; }
	}
}
