package com.hirelink.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.hirelink.global.exception.SocialAuthException;

@Component
public class RegistrationCipher {

	private final String configuredKey;
	private final SecureRandom random = new SecureRandom();

	public RegistrationCipher(@Value("${registration-verification.encryption-key:}") String configuredKey) {
		this.configuredKey = configuredKey;
	}

	public void requireConfigured() { key(); }

	public String encrypt(String plaintext, String sessionHash) {
		byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
		try {
			byte[] iv = new byte[12];
			random.nextBytes(iv);
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, sessionHash);
			return "v1." + Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(bytes));
		} catch (GeneralSecurityException exception) {
			throw unavailable();
		} finally {
			Arrays.fill(bytes, (byte) 0);
		}
	}

	public String decrypt(String encrypted, String sessionHash) {
		byte[] bytes = null;
		try {
			String[] parts = encrypted.split("\\.");
			if (parts.length != 3 || !parts[0].equals("v1")) { throw new IllegalArgumentException(); }
			byte[] iv = Base64.getDecoder().decode(parts[1]);
			if (iv.length != 12) { throw new IllegalArgumentException(); }
			bytes = cipher(Cipher.DECRYPT_MODE, iv, sessionHash).doFinal(Base64.getDecoder().decode(parts[2]));
			return new String(bytes, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw unavailable();
		} finally {
			if (bytes != null) { Arrays.fill(bytes, (byte) 0); }
		}
	}

	private Cipher cipher(int mode, byte[] iv, String sessionHash) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, key(), new GCMParameterSpec(128, iv));
		cipher.updateAAD(("registration:v1:" + sessionHash).getBytes(StandardCharsets.UTF_8));
		return cipher;
	}

	private SecretKeySpec key() {
		byte[] bytes = null;
		try {
			bytes = Base64.getDecoder().decode(configuredKey);
			if (bytes.length != 32) { throw new IllegalArgumentException(); }
			return new SecretKeySpec(bytes, "AES");
		} catch (IllegalArgumentException exception) {
			throw unavailable();
		} finally {
			if (bytes != null) { Arrays.fill(bytes, (byte) 0); }
		}
	}

	private SocialAuthException unavailable() {
		return new SocialAuthException(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRATION_VERIFICATION_UNAVAILABLE", "등록증 확인 서비스를 이용할 수 없습니다.");
	}
}
