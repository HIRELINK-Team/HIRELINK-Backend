package com.hirelink.domain.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.entity.PhoneVerification;
import com.hirelink.domain.auth.dto.PhoneVerificationConfirmResponse;
import com.hirelink.domain.auth.entity.PhoneVerificationSendAttempt;
import com.hirelink.domain.auth.repository.PhoneVerificationRepository;
import com.hirelink.domain.auth.repository.PhoneVerificationSendAttemptRepository;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.PhoneVerificationRateLimitException;
import com.hirelink.global.exception.SmsDeliveryException;
import com.hirelink.global.exception.WithdrawnAccountException;
import com.hirelink.global.exception.DuplicatePhoneNumberException;
import com.hirelink.global.exception.InvalidPhoneVerificationCodeException;
import com.hirelink.global.sms.SmsSendRequest;
import com.hirelink.global.sms.SmsSender;

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
		findActiveUserForUpdate(userId);
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
		// 발송 실패도 요청 횟수에 포함한다.
		PhoneVerificationSendAttempt sendAttempt = sendAttemptRepository.saveAndFlush(
				new PhoneVerificationSendAttempt(userId, phoneNumber, now));
		String code;
		do {
			code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
		} while (verification != null && passwordEncoder.matches(code, verification.getCodeHash()));
		String codeHash = passwordEncoder.encode(code);
		Instant sentAt = Instant.now();
		if (verification == null) {
			verification = new PhoneVerification(userId, phoneNumber, codeHash, sentAt.plus(CODE_TTL));
		} else {
			verification.replaceCode(phoneNumber, codeHash, sentAt, sentAt.plus(CODE_TTL));
		}
		verificationRepository.saveAndFlush(verification);
		try {
			smsSender.sendAndWait(new SmsSendRequest(phoneNumber, "[HIRELINK] 인증번호 [" + code + "]",
					"phone-verification:" + sendAttempt.getId()));
		} catch (SmsDeliveryException exception) {
			verificationRepository.delete(verification);
			verificationRepository.flush();
			throw exception;
		}
	}

	// 입력 오류 응답을 반환해도 실패 횟수는 커밋한다. 저장 오류와 중복 번호는 롤백한다.
	@Transactional(noRollbackFor = {InvalidPhoneVerificationCodeException.class, PhoneVerificationRateLimitException.class})
	public PhoneVerificationConfirmResponse confirmCode(Long userId, String phoneNumber, String code) {
		UserAccount user = findActiveUserForUpdate(userId);
		Instant now = Instant.now();
		PhoneVerification verification = verificationRepository.findByUserId(userId)
				.orElseThrow(InvalidPhoneVerificationCodeException::new);
		if (!verification.getPhoneNumber().equals(phoneNumber) || !verification.isActive(now)) {
			throw new InvalidPhoneVerificationCodeException();
		}
		if (verification.isBlocked(now)) {
			throw new PhoneVerificationRateLimitException();
		}
		if (!passwordEncoder.matches(code, verification.getCodeHash())) {
			verification.recordFailure();
			if (verification.isBlocked(now)) {
				throw new PhoneVerificationRateLimitException();
			}
			throw new InvalidPhoneVerificationCodeException();
		}

		// 가입 완료와 기존 회원 인증이 같은 번호를 동시에 저장하지 않도록 직렬화한다.
		sendAttemptRepository.lockPhoneNumber(phoneNumber);
		user.verifyPhoneNumber(phoneNumber, now);
		try {
			userAccountRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicatePhoneNumber(exception)) {
				throw new DuplicatePhoneNumberException();
			}
			throw exception;
		}
		verificationRepository.delete(verification);
		String formattedPhoneNumber = phoneNumber.substring(0, 3) + "-" + phoneNumber.substring(3, 7)
				+ "-" + phoneNumber.substring(7);
		return new PhoneVerificationConfirmResponse(formattedPhoneNumber, now, "휴대전화 인증이 완료되었습니다.");
	}

	private UserAccount findActiveUserForUpdate(Long userId) {
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		return user;
	}

	private boolean isDuplicatePhoneNumber(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				return "uk_user_account_phone_number".equals(constraintViolation.getConstraintName());
			}
		}
		return false;
	}
}
