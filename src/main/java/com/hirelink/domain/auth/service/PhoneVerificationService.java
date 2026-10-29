package com.hirelink.domain.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.entity.PhoneVerification;
import com.hirelink.domain.auth.entity.PhoneVerificationSendAttempt;
import com.hirelink.domain.auth.repository.PhoneVerificationRepository;
import com.hirelink.domain.auth.repository.PhoneVerificationSendAttemptRepository;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.PhoneVerificationRateLimitException;
import com.hirelink.global.exception.SmsDeliveryException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class PhoneVerificationService {

	public static final Duration CODE_TTL = Duration.ofSeconds(300);
	private final SecureRandom secureRandom = new SecureRandom();
	private final UserAccountRepository userAccountRepository;
	private final PhoneVerificationRepository verificationRepository;
	private final PhoneVerificationSendAttemptRepository sendAttemptRepository;
	private final PasswordEncoder passwordEncoder;
	private final SmsSender smsSender;

	public PhoneVerificationService(UserAccountRepository userAccountRepository,
			PhoneVerificationRepository verificationRepository,
			PhoneVerificationSendAttemptRepository sendAttemptRepository,
			PasswordEncoder passwordEncoder, SmsSender smsSender) {
		this.userAccountRepository = userAccountRepository;
		this.verificationRepository = verificationRepository;
		this.sendAttemptRepository = sendAttemptRepository;
		this.passwordEncoder = passwordEncoder;
		this.smsSender = smsSender;
	}

	@Transactional(noRollbackFor = SmsDeliveryException.class)
	public void requestCode(Long userId, String phoneNumber) {
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		// 계정 → 번호 순서로 잠가 동시 요청도 같은 발송 제한을 적용한다.
		sendAttemptRepository.lockPhoneNumber(phoneNumber);
		Instant now = Instant.now();
		PhoneVerification verification = verificationRepository.findByUserId(userId).orElse(null);
		if (verification != null && verification.isBlocked(now)) {
			throw new PhoneVerificationRateLimitException();
		}
		if (sendAttemptRepository.countByUserIdAndRequestedAtAfter(userId, now.minusSeconds(60)) > 0
				|| sendAttemptRepository.countByPhoneNumberAndRequestedAtAfter(phoneNumber, now.minusSeconds(60)) > 0
				|| sendAttemptRepository.countByUserIdAndRequestedAtAfter(userId, now.minusSeconds(3600)) >= 5
				|| sendAttemptRepository.countByPhoneNumberAndRequestedAtAfter(phoneNumber, now.minusSeconds(3600)) >= 5) {
			throw new PhoneVerificationRateLimitException();
		}
		sendAttemptRepository.deleteExpiredAttempts(userId, phoneNumber, now.minusSeconds(3600));
		// 발송 실패도 요청 횟수에 포함하되 기존 인증번호는 유지한다.
		sendAttemptRepository.saveAndFlush(new PhoneVerificationSendAttempt(userId, phoneNumber, now));
		String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
		String codeHash = passwordEncoder.encode(code);
		smsSender.sendVerificationCode(phoneNumber, code);
		Instant sentAt = Instant.now();
		if (verification == null) {
			verification = new PhoneVerification(userId, phoneNumber, codeHash, sentAt.plus(CODE_TTL));
		} else {
			verification.replaceCode(phoneNumber, codeHash, sentAt, sentAt.plus(CODE_TTL));
		}
		verificationRepository.save(verification);
	}
}
