package com.hirelink.domain.developerinquiry.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.developerinquiry.dto.DeveloperInquiryCreateRequest;
import com.hirelink.domain.developerinquiry.dto.DeveloperInquiryCreateResponse;
import com.hirelink.domain.developerinquiry.entity.DeveloperInquiry;
import com.hirelink.domain.developerinquiry.repository.DeveloperInquiryRepository;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.InquiryReplyEmailRequiredException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class DeveloperInquiryService {

	private final DeveloperInquiryRepository inquiries;
	private final UserAccountRepository users;
	private final Clock clock;

	public DeveloperInquiryService(DeveloperInquiryRepository inquiries, UserAccountRepository users, Clock clock) {
		this.inquiries = inquiries;
		this.users = users;
		this.clock = clock;
	}

	@Transactional
	public DeveloperInquiryCreateResponse create(Long userId, DeveloperInquiryCreateRequest request) {
		UserAccount user = users.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		String email = user.getEmail();
		if (email == null || email.isBlank()) {
			throw new InquiryReplyEmailRequiredException();
		}
		Instant submittedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		DeveloperInquiry inquiry = inquiries.save(new DeveloperInquiry(user.getId(), user.getRole(), email,
				request.inquiryType().trim(), request.title().trim(), request.content().trim(), submittedAt));
		return new DeveloperInquiryCreateResponse(inquiry.getId(), inquiry.getSubmittedAt());
	}
}
