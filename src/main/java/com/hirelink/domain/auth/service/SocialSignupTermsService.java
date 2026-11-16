package com.hirelink.domain.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.auth.dto.SocialSignupTermsRequest;
import com.hirelink.domain.auth.dto.SocialSignupTermsResponse;
import com.hirelink.domain.auth.entity.SocialAuthSession;
import com.hirelink.domain.auth.entity.SocialSignupAgreement;
import com.hirelink.domain.member.entity.TermType;
import com.hirelink.domain.member.repository.TermTypeRepository;
import com.hirelink.global.exception.DuplicateTermTypeException;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.exception.TermTypeNotFoundException;

@Service
@Transactional
public class SocialSignupTermsService {

	private final SocialAuthService socialAuthService;
	private final TermTypeRepository termTypeRepository;

	public SocialSignupTermsService(SocialAuthService socialAuthService, TermTypeRepository termTypeRepository) {
		this.socialAuthService = socialAuthService;
		this.termTypeRepository = termTypeRepository;
	}

	public SocialSignupTermsResponse getTerms(String token) {
		SocialAuthSession session = socialAuthService.requireSignupSession(token);
		return response(termTypeRepository.findBySocialSignupTrueOrderByIdAsc(), session);
	}

	public SocialSignupTermsResponse saveTerms(String token, SocialSignupTermsRequest request) {
		SocialAuthSession session = socialAuthService.requireSignupSession(token);
		List<TermType> terms = termTypeRepository.findBySocialSignupTrueOrderByIdAsc();
		Set<Long> termIds = terms.stream().map(TermType::getId).collect(Collectors.toSet());
		Map<Long, Boolean> requested = new HashMap<>();
		for (SocialSignupTermsRequest.Agreement agreement : request.agreements()) {
			if (requested.putIfAbsent(agreement.termTypeId(), agreement.isAgreed()) != null) {
				throw new DuplicateTermTypeException();
			}
			if (!termIds.contains(agreement.termTypeId())) {
				throw new TermTypeNotFoundException();
			}
		}
		for (TermType term : terms) {
			if (term.isRequired() && !Boolean.TRUE.equals(requested.get(term.getId()))) {
				throw new SocialAuthException(HttpStatus.BAD_REQUEST, "REQUIRED_TERMS_NOT_AGREED", "필수 약관에 모두 동의해주세요.");
			}
		}
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		for (TermType term : terms) {
			session.saveAgreement(term.getId(), requested.getOrDefault(term.getId(), false), now);
		}
		return response(terms, session);
	}

	private SocialSignupTermsResponse response(List<TermType> terms, SocialAuthSession session) {
		Map<Long, SocialSignupAgreement> agreements = session.getAgreements().stream()
				.collect(Collectors.toMap(SocialSignupAgreement::getTermTypeId, agreement -> agreement));
		return new SocialSignupTermsResponse(terms.stream().map(term -> {
			SocialSignupAgreement agreement = agreements.get(term.getId());
			return new SocialSignupTermsResponse.Term(term.getId(), term.getTermTypeName(), term.isRequired(),
					agreement != null && agreement.isAgreed(), agreement == null ? null : agreement.getAgreedAt());
		}).toList());
	}
}
