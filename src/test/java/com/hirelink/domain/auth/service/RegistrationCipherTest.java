package com.hirelink.domain.auth.service;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.hirelink.global.exception.SocialAuthException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationCipherTest {
	private final RegistrationCipher cipher = new RegistrationCipher(Base64.getEncoder().encodeToString(new byte[32]));

	@Test
	void encryptsWithUniqueNonceAndBindsCiphertextToSession() {
		String plain = "synthetic-registration-information";
		String first = cipher.encrypt(plain, "session-one");
		assertThat(first).doesNotContain(plain).isNotEqualTo(cipher.encrypt(plain, "session-one"));
		assertThat(cipher.decrypt(first, "session-one")).isEqualTo(plain);
		assertThatThrownBy(() -> cipher.decrypt(first, "session-two")).isInstanceOf(SocialAuthException.class).hasNoCause();
		String[] parts = first.split("\\.");
		byte[] tampered = Base64.getDecoder().decode(parts[2]);
		tampered[0] ^= 1;
		assertThatThrownBy(() -> cipher.decrypt(parts[0] + "." + parts[1] + "." + Base64.getEncoder().encodeToString(tampered), "session-one"))
				.isInstanceOf(SocialAuthException.class).hasNoCause().hasMessageNotContaining(plain);
	}

	@Test
	void rejectsMissingWrongLengthAndWrongKeysWithoutLeakingInput() {
		for (String key : List.of("", "not-base64", Base64.getEncoder().encodeToString(new byte[16]))) {
			assertThatThrownBy(() -> new RegistrationCipher(key).encrypt("sensitive", "session"))
					.isInstanceOf(SocialAuthException.class).hasNoCause().hasMessageNotContaining("sensitive");
		}
		byte[] other = new byte[32];
		other[0] = 1;
		assertThatThrownBy(() -> new RegistrationCipher(Base64.getEncoder().encodeToString(other))
				.decrypt(cipher.encrypt("sensitive", "session"), "session")).isInstanceOf(SocialAuthException.class);
	}
}
