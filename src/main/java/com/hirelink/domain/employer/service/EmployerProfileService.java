package com.hirelink.domain.employer.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.employer.dto.EmployerProfileCreateRequest;
import com.hirelink.domain.employer.dto.EmployerProfileCreateResponse;
import com.hirelink.domain.employer.entity.EmployerProfile;
import com.hirelink.domain.employer.repository.EmployerProfileRepository;
import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.DuplicateEmployerProfileException;
import com.hirelink.global.exception.PhoneNotVerifiedException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class EmployerProfileService {

	private final UserAccountRepository userAccountRepository;
	private final EmployerProfileRepository employerProfileRepository;

	public EmployerProfileService(UserAccountRepository userAccountRepository,
			EmployerProfileRepository employerProfileRepository) {
		this.userAccountRepository = userAccountRepository;
		this.employerProfileRepository = employerProfileRepository;
	}

	@Transactional
	public EmployerProfileCreateResponse createProfile(Long userId, EmployerProfileCreateRequest request) {
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 프로필을 등록할 수 있습니다.");
		}
		if (!user.isPhoneVerified()) {
			throw new PhoneNotVerifiedException();
		}
		if (employerProfileRepository.existsById(userId)) {
			throw new DuplicateEmployerProfileException();
		}

		user.updateName(request.getName());
		user.updateAcquisitionChannel(request.getAcquisitionChannel());
		EmployerProfile profile = new EmployerProfile(user);
		employerProfileRepository.saveAndFlush(profile);

		return new EmployerProfileCreateResponse(profile.getId(), user.getName(), user.getEmail(),
				formatPhoneNumber(user.getPhoneNumber()), user.getAcquisitionChannel(), profile.getPosition());
	}

	private String formatPhoneNumber(String phoneNumber) {
		return phoneNumber.substring(0, 3) + "-" + phoneNumber.substring(3, 7) + "-" + phoneNumber.substring(7);
	}
}
