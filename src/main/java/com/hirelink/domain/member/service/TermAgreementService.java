package com.hirelink.domain.member.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.dto.TermAgreementRequest;
import com.hirelink.domain.member.dto.TermAgreementResponse;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.entity.UserTermAgreement;
import com.hirelink.domain.member.entity.UserTermAgreementId;
import com.hirelink.domain.member.repository.TermTypeRepository;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.member.repository.UserTermAgreementRepository;
import com.hirelink.global.exception.DuplicateTermTypeException;
import com.hirelink.global.exception.TermTypeNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class TermAgreementService {

	private final UserAccountRepository userAccountRepository;
	private final TermTypeRepository termTypeRepository;
	private final UserTermAgreementRepository agreementRepository;

	public TermAgreementService(UserAccountRepository userAccountRepository, TermTypeRepository termTypeRepository,
			UserTermAgreementRepository agreementRepository) {
		this.userAccountRepository = userAccountRepository;
		this.termTypeRepository = termTypeRepository;
		this.agreementRepository = agreementRepository;
	}

	@Transactional
	public TermAgreementResponse saveAgreements(Long userId, TermAgreementRequest request) {
		// 같은 회원의 동시 요청을 순서대로 처리하여 중복 저장을 방지한다.
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}

		Set<Long> termTypeIds = new HashSet<>();
		for (TermAgreementRequest.Agreement item : request.agreements()) {
			if (!termTypeIds.add(item.termTypeId())) {
				throw new DuplicateTermTypeException();
			}
		}
		if (termTypeRepository.findAllById(termTypeIds).size() != termTypeIds.size()) {
			throw new TermTypeNotFoundException();
		}

		// PostgreSQL의 시간 정밀도에 맞춰 최초 응답과 재요청 응답의 시각을 동일하게 유지한다.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		List<TermAgreementResponse.Agreement> savedAgreements = new ArrayList<>();
		for (TermAgreementRequest.Agreement item : request.agreements()) {
			UserTermAgreementId id = new UserTermAgreementId(userId, item.termTypeId());
			UserTermAgreement agreement = agreementRepository.findById(id)
					.orElseGet(() -> new UserTermAgreement(id));
			agreement.updateAgreement(item.isAgreed(), now);
			agreementRepository.save(agreement);
			savedAgreements.add(new TermAgreementResponse.Agreement(
					item.termTypeId(), agreement.isAgreed(), agreement.getAgreedAt()));
		}
		return new TermAgreementResponse(savedAgreements);
	}
}
