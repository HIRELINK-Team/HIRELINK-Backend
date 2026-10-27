package com.hirelink.global.security;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

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
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
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
		return createDecoder(jwtSigningKey, issuer, "access");
	}

	@Bean
	public JwtDecoder refreshJwtDecoder(SecretKey jwtSigningKey, @Value("${jwt.issuer}") String issuer) {
		return createDecoder(jwtSigningKey, issuer, "refresh");
	}

	private JwtDecoder createDecoder(SecretKey jwtSigningKey, String issuer, String tokenType) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
				.macAlgorithm(MacAlgorithm.HS256).build();
		MappedJwtClaimSetConverter claimConverter = MappedJwtClaimSetConverter.withDefaults(Map.of());
		decoder.setClaimSetConverter(claims -> {
			// 기본 변환기가 누락된 발급 시각을 채우기 전에 원본의 필수 값을 확인한다.
			if (claims.get("iat") == null) {
				throw new BadJwtException("발급 시각이 없는 토큰입니다.");
			}
			return claimConverter.convert(claims);
		});
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(Duration.ZERO),
				new JwtIssuerValidator(issuer),
				jwt -> validateTokenClaims(jwt, tokenType)));
		return decoder;
	}

	private OAuth2TokenValidatorResult validateTokenClaims(Jwt jwt, String tokenType) {
		try {
			boolean valid = tokenType.equals(jwt.getClaimAsString("token_type"))
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
				new OAuth2Error("invalid_token", "유효한 토큰이 아닙니다.", null));
	}
}
