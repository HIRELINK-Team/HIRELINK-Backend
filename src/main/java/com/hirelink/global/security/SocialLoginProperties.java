package com.hirelink.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.social")
public record SocialLoginProperties(boolean enabled, String callbackBaseUrl, String frontendRedirectUri,
		Provider google, Provider kakao) {

	public record Provider(String clientId, String clientSecret, String authorizationUri,
			String tokenUri, String jwkSetUri, String issuerUri) {
		@Override
		public String toString() { return "SocialProvider[credentials=hidden]"; }
	}
}
