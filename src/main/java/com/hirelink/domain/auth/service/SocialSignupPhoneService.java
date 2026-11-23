package com.hirelink.domain.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.dto.AuthResult;
import com.hirelink.domain.auth.entity.AuthAccount;
import com.hirelink.domain.auth.entity.PhoneVerification;
import com.hirelink.domain.auth.entity.PhoneVerificationSendAttempt;
import com.hirelink.domain.auth.entity.SocialAuthSession;
import com.hirelink.domain.auth.entity.SocialSignupAgreement;
import com.hirelink.domain.auth.repository.AuthAccountRepository;
import com.hirelink.domain.auth.repository.PhoneVerificationRepository;
import com.hirelink.domain.auth.repository.PhoneVerificationSendAttemptRepository;
import com.hirelink.domain.auth.repository.SocialAuthSessionRepository;
import com.hirelink.domain.member.entity.TermType;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.entity.UserTermAgreement;
import com.hirelink.domain.member.entity.UserTermAgreementId;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.member.repository.UserTermAgreementRepository;
import com.hirelink.global.exception.InvalidPhoneVerificationCodeException;
import com.hirelink.global.exception.PhoneVerificationRateLimitException;
import com.hirelink.global.exception.SmsDeliveryException;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.sms.SmsSendRequest;
import com.hirelink.global.sms.SmsSender;

@Service
public class SocialSignupPhoneService {

	private final SocialAuthService socialAuthService;
	private final SocialAuthSessionRepository sessions;
	private final PhoneVerificationRepository verifications;
	private final PhoneVerificationSendAttemptRepository attempts;
	private final UserAccountRepository users;
	private final AuthAccountRepository accounts;
	private final SocialSignupTermsService terms;
	private final UserTermAgreementRepository agreements;
	private final PasswordEncoder passwordEncoder;
	private final SmsSender smsSender;
	private final TokenService tokenService;
	private final SocialSignupRegistrationTransferService registrationTransfer;
	private final SecureRandom random = new SecureRandom();

	public SocialSignupPhoneService(SocialAuthService socialAuthService, SocialAuthSessionRepository sessions,
			PhoneVerificationRepository verifications, PhoneVerificationSendAttemptRepository attempts,
			UserAccountRepository users, AuthAccountRepository accounts, SocialSignupTermsService terms,
			UserTermAgreementRepository agreements, PasswordEncoder passwordEncoder, SmsSender smsSender,
			TokenService tokenService, SocialSignupRegistrationTransferService registrationTransfer) {
		this.socialAuthService = socialAuthService;
		this.sessions = sessions;
		this.verifications = verifications;
		this.attempts = attempts;
		this.users = users;
		this.accounts = accounts;
		this.terms = terms;
		this.agreements = agreements;
		this.passwordEncoder = passwordEncoder;
		this.smsSender = smsSender;
		this.tokenService = tokenService;
		this.registrationTransfer = registrationTransfer;
	}

	@Transactional(noRollbackFor = SmsDeliveryException.class)
	public void requestCode(String token, String phoneNumber) {
		SocialAuthSession session = socialAuthService.requireSignupSession(token);
		requireAgreements(session);
		registrationTransfer.requireVerified(session);
		String identity = socialAuthService.signupIdentity(session);
		attempts.lockPhoneNumber(phoneNumber);
		Instant now = Instant.now();
		requireUnexpired(session, now);
		registrationTransfer.requireVerified(session);
		PhoneVerification verification = verifications.findBySignupTokenHash(session.getTokenHash()).orElse(null);
		if (verification != null && verification.isBlocked(now)) {
			throw new PhoneVerificationRateLimitException();
		}
		if (attempts.countBySignupIdentityAndRequestedAtAfter(identity, now.minusSeconds(60)) > 0
				|| attempts.countByPhoneNumberAndRequestedAtAfter(phoneNumber, now.minusSeconds(60)) > 0
				|| attempts.countBySignupIdentityAndRequestedAtAfter(identity, now.minusSeconds(3600)) >= 5
				|| attempts.countByPhoneNumberAndRequestedAtAfter(phoneNumber, now.minusSeconds(3600)) >= 5) {
			throw new PhoneVerificationRateLimitException();
		}
		attempts.deleteExpiredSignupAttempts(identity, phoneNumber, now.minusSeconds(3600));
		PhoneVerificationSendAttempt sendAttempt = attempts.saveAndFlush(
				PhoneVerificationSendAttempt.forSignup(identity, phoneNumber, now));
		String code;
		do {
			code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
		} while (verification != null && passwordEncoder.matches(code, verification.getCodeHash()));
		String codeHash = passwordEncoder.encode(code);
		Instant sentAt = Instant.now();
		requireUnexpired(session, sentAt);
		registrationTransfer.requireVerified(session);
		if (verification == null) {
			verification = PhoneVerification.forSignup(session.getTokenHash(), phoneNumber, codeHash,
					sentAt.plus(PhoneVerificationService.CODE_TTL));
		} else {
			verification.replaceCode(phoneNumber, codeHash, sentAt, sentAt.plus(PhoneVerificationService.CODE_TTL));
		}
		verifications.saveAndFlush(verification);
		try {
			smsSender.sendAndWait(new SmsSendRequest(phoneNumber, "[HIRELINK] 인증번호 [" + code + "]",
					"social-phone-verification:" + sendAttempt.getId()));
		} catch (SmsDeliveryException exception) {
			verifications.delete(verification);
			verifications.flush();
			throw exception;
		}
	}

	@Transactional(noRollbackFor = {InvalidPhoneVerificationCodeException.class, PhoneVerificationRateLimitException.class})
	public Optional<AuthResult> confirmCode(String token, String phoneNumber, String code) {
		SocialAuthSession session = socialAuthService.requireSignupSession(token);
		List<TermType> signupTerms = requireAgreements(session);
		registrationTransfer.requireVerified(session);
		PhoneVerification verification = verifications.findBySignupTokenHash(session.getTokenHash())
				.orElseThrow(InvalidPhoneVerificationCodeException::new);
		Instant now = Instant.now();
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
		attempts.lockPhoneNumber(phoneNumber);
		now = Instant.now();
		requireUnexpired(session, now);
		if (!verification.isActive(now)) { throw new InvalidPhoneVerificationCodeException(); }
		var registration = registrationTransfer.requireVerified(session);
		if (users.existsByPhoneNumberAndWithdrawalFinalizedAtIsNull(phoneNumber)) {
			// 중복 안내는 정상 반환하여 이번 가입 정보 삭제를 커밋한다.
			sessions.delete(session);
			return Optional.empty();
		}
		UserAccount user = new UserAccount(session.getEmail(), session.getRole());
		user.verifyPhoneNumber(phoneNumber, now);
		users.saveAndFlush(user);
		AuthAccount account=new AuthAccount(user, session.getProvider(), session.getProviderUserId(), null);
        account.saveRevocationToken(session.getRevocationToken());
        accounts.saveAndFlush(account);
		registrationTransfer.save(user, registration, now);
		Map<Long, SocialSignupAgreement> saved = agreementMap(session);
		for (TermType term : signupTerms) {
			SocialSignupAgreement consent = saved.get(term.getId());
			UserTermAgreement agreement = new UserTermAgreement(new UserTermAgreementId(user.getId(), term.getId()));
			agreement.updateAgreement(consent != null && consent.isAgreed(), consent == null ? null : consent.getAgreedAt(), consent == null ? null : consent.getTermVersion());
			agreements.save(agreement);
		}
		attempts.transferSignupAttempts(socialAuthService.signupIdentity(session), user.getId());
		AuthResult result = tokenService.issueTokens(user);
		requireUnexpired(session, Instant.now());
		registrationTransfer.requireVerified(session);
		sessions.delete(session);
		return Optional.of(result);
	}

	private void requireUnexpired(SocialAuthSession session, Instant now) {
		if (!session.getExpiresAt().isAfter(now)) {
			throw new SocialAuthException(HttpStatus.UNAUTHORIZED, "INVALID_SOCIAL_SESSION", "소셜 인증을 다시 진행해주세요.");
		}
	}

	private List<TermType> requireAgreements(SocialAuthSession session) {
		List<TermType> signupTerms = terms.signupTerms(session);
		Map<Long, SocialSignupAgreement> saved = agreementMap(session);
		for (TermType term : signupTerms) {
			SocialSignupAgreement consent = saved.get(term.getId());
			if (term.isRequired() && (consent == null || !consent.isAgreed() || consent.getAgreedAt() == null || consent.getTermVersion() == null)) {
				throw new SocialAuthException(HttpStatus.BAD_REQUEST, "REQUIRED_TERMS_NOT_AGREED", "필수 약관에 모두 동의해주세요.");
			}
		}
		return signupTerms;
	}

	private Map<Long, SocialSignupAgreement> agreementMap(SocialAuthSession session) {
		return session.getAgreements().stream()
				.collect(Collectors.toMap(SocialSignupAgreement::getTermTypeId, agreement -> agreement));
	}
}
