package com.hirelink.domain.auth;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.hirelink.domain.auth.entity.PhoneVerification;
import static org.assertj.core.api.Assertions.assertThat;

class PhoneChangeConfirmationTest {
	@Test
	void fiveMinutesStartAtConfirmationAndExpiryIsExclusive() {
		Instant now = Instant.parse("2026-11-23T00:00:00Z");
		var proof = new PhoneVerification(1L, "01012345678", "hash", now.plusSeconds(10));
		assertThat(proof.permitsChange("01012345678", now)).isFalse();
		proof.confirmChange(now.plusSeconds(9));
		assertThat(proof.permitsChange("01012345678", now.plusSeconds(308))).isTrue();
		assertThat(proof.permitsChange("01012345678", now.plusSeconds(309))).isFalse();
		assertThat(proof.permitsChange("01087654321", now.plusSeconds(10))).isFalse();
		assertThat(proof.isActive(now.plusSeconds(9))).isFalse();
	}
}
