package com.hirelink.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.dto.RefreshResponse;
import com.hirelink.domain.auth.entity.RefreshToken;
import com.hirelink.domain.auth.repository.RefreshTokenRepository;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.global.exception.InvalidRefreshTokenException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class TokenService {

	public static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
	public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

	private final JwtEncoder jwtEncoder;
	private final JwtDecoder refreshJwtDecoder;
	private final RefreshTokenRepository refreshTokenRepository;
	private final String issuer;

	public TokenService(JwtEncoder jwtEncoder, @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
			RefreshTokenRepository refreshTokenRepository,
			@Value("${jwt.issuer}") String issuer) {
		this.jwtEncoder = jwtEncoder;
		this.refreshJwtDecoder = refreshJwtDecoder;
		this.refreshTokenRepository = refreshTokenRepository;
		this.issuer = issuer;
	}

	@Transactional
	public AuthResult issueTokens(UserAccount userAccount) {
		Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant refreshExpiresAt = issuedAt.plus(REFRESH_TOKEN_TTL);
		String accessToken = createToken(userAccount, "access", issuedAt, issuedAt.plus(ACCESS_TOKEN_TTL));
		String refreshToken = createToken(userAccount, "refresh", issuedAt, refreshExpiresAt);
		refreshTokenRepository.saveAndFlush(new RefreshToken(
				userAccount, hashToken(refreshToken), refreshExpiresAt, issuedAt));
		return new AuthResult(userAccount.getId(), userAccount.getEmail(), userAccount.getRole(),
				accessToken, refreshToken);
	}

	@Transactional(readOnly = true)
	public RefreshResponse refreshAccessToken(String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidRefreshTokenException();
		}

		Jwt jwt;
		try {
			jwt = refreshJwtDecoder.decode(token);
		} catch (JwtException | IllegalArgumentException exception) {
			throw new InvalidRefreshTokenException();
		}
		RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(token))
				.orElseThrow(InvalidRefreshTokenException::new);
		UserAccount userAccount = refreshToken.getUserAccount();
		if (!refreshToken.getExpiresAt().isAfter(Instant.now())
				|| !userAccount.getId().toString().equals(jwt.getSubject())) {
			throw new InvalidRefreshTokenException();
		}
		if (userAccount.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}

		Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		String accessToken = createToken(userAccount, "access", issuedAt, issuedAt.plus(ACCESS_TOKEN_TTL));
		return new RefreshResponse(accessToken, "Bearer", ACCESS_TOKEN_TTL.toSeconds());
	}

	private String createToken(UserAccount userAccount, String tokenType, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(userAccount.getId().toString())
				.claim("role", userAccount.getRole().name())
				.claim("token_type", tokenType)
				.id(UUID.randomUUID().toString())
				.issuedAt(issuedAt)
				.notBefore(issuedAt)
				.expiresAt(expiresAt)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private String hashToken(String token) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("토큰 해시를 생성할 수 없습니다.", exception);
		}
	}
}
