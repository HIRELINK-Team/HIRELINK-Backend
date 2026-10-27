package com.hirelink.global.security;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.hirelink.domain.member.entity.MemberRole;

@Configuration
public class JwtConfig {

	@Bean
	public SecretKey jwtSigningKey(@Value("${jwt.secret}") String secret) {
		byte[] keyBytes = Base64.getDecoder().decode(secret);
		if (keyBytes.length < 32) {
			throw new IllegalArgumentException("JWT_SECRET은 32바이트 이상 난수의 Base64 값이어야 합니다.");
		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, @Value("${jwt.issuer}") String issuer) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
				.macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(Duration.ZERO),
				new JwtIssuerValidator(issuer),
				this::validateAccessTokenClaims));
		return decoder;
	}

	private OAuth2TokenValidatorResult validateAccessTokenClaims(Jwt jwt) {
		try {
			boolean valid = "access".equals(jwt.getClaimAsString("token_type"))
					&& jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
					&& Long.parseLong(jwt.getSubject()) > 0;
			MemberRole.valueOf(jwt.getClaimAsString("role"));
			if (valid) {
				return OAuth2TokenValidatorResult.success();
			}
		} catch (IllegalArgumentException | NullPointerException exception) {
			// 필수 식별 정보가 없는 토큰은 인증에 사용하지 않는다.
		}
		return OAuth2TokenValidatorResult.failure(
				new OAuth2Error("invalid_token", "유효한 Access Token이 아닙니다.", null));
	}
}
