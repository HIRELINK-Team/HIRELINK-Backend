package com.hirelink.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.entity.RefreshToken;
import com.hirelink.domain.auth.repository.RefreshTokenRepository;
import com.hirelink.domain.member.entity.UserAccount;

@Service
public class TokenService {

	public static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
	public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

	private final JwtEncoder jwtEncoder;
	private final RefreshTokenRepository refreshTokenRepository;
	private final String issuer;

	public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokenRepository,
			@Value("${jwt.issuer}") String issuer) {
		this.jwtEncoder = jwtEncoder;
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
