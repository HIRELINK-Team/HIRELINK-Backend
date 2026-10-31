package com.hirelink.domain.employmentoffer.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.application.repository.ApplicationRepository;
import com.hirelink.domain.employmentoffer.dto.EmploymentOfferRejectResponse;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;
import com.hirelink.domain.employmentoffer.repository.EmploymentOfferRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.ApplicationNotFoundException;
import com.hirelink.global.exception.EmploymentOfferNotFoundException;
import com.hirelink.global.exception.EmploymentOfferStateConflictException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class EmploymentOfferService {

	private final UserAccountRepository userAccountRepository;
	private final ApplicationRepository applicationRepository;
	private final EmploymentOfferRepository employmentOfferRepository;
	private final Clock clock;

	public EmploymentOfferService(UserAccountRepository userAccountRepository,
			ApplicationRepository applicationRepository,
			EmploymentOfferRepository employmentOfferRepository,
			Clock clock) {
		this.userAccountRepository = userAccountRepository;
		this.applicationRepository = applicationRepository;
		this.employmentOfferRepository = employmentOfferRepository;
		this.clock = clock;
	}

	@Transactional
	public EmploymentOfferRejectResponse rejectOffer(Long userId, Long offerId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.STUDENT) {
			throw new AccessDeniedException("유학생 계정만 채용 제안을 거절할 수 있습니다.");
		}

		Long applicationId = employmentOfferRepository.findApplicationIdById(offerId)
				.orElseThrow(EmploymentOfferNotFoundException::new);

		// 동일 지원을 다루는 모든 처리는 Application -> EmploymentOffer 순서로 잠금한다.
		Application application = applicationRepository.findByIdForUpdate(applicationId)
				.orElseThrow(ApplicationNotFoundException::new);
		EmploymentOffer offer = employmentOfferRepository.findByIdForUpdate(offerId)
				.orElseThrow(EmploymentOfferNotFoundException::new);

		if (!offer.getApplication().getId().equals(application.getId())) {
			throw new EmploymentOfferStateConflictException();
		}
		if (!application.getStudentProfile().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 채용 제안만 거절할 수 있습니다.");
		}

		Instant processedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		if (offer.getStatus() != EmploymentOfferStatus.PENDING
				|| application.getStatus() != ApplicationStatus.OFFERED
				|| !offer.getStudentResponseDeadline().isAfter(processedAt)) {
			throw new EmploymentOfferStateConflictException();
		}

		offer.reject(processedAt);
		application.rejectOffer(processedAt);
		employmentOfferRepository.flush();

		return new EmploymentOfferRejectResponse(offer.getId(), application.getId(), offer.getStatus(),
				application.getStatus(), application.getEndReason(), offer.getRespondedAt(), offer.getEndedAt());
	}
}
