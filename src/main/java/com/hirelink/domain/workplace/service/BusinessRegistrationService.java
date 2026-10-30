package com.hirelink.domain.workplace.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.workplace.client.NtsBusinessRegistrationClient;
import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyRequest;
import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyResponse;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class BusinessRegistrationService {

	private final UserAccountRepository userAccountRepository;
	private final NtsBusinessRegistrationClient ntsClient;

	public BusinessRegistrationService(UserAccountRepository userAccountRepository, NtsBusinessRegistrationClient ntsClient) {
		this.userAccountRepository = userAccountRepository;
		this.ntsClient = ntsClient;
	}

	public BusinessRegistrationVerifyResponse verify(Long userId, BusinessRegistrationVerifyRequest request) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 사업자등록번호를 확인할 수 있습니다.");
		}

		String number = request.getBusinessRegistrationNumber().replace("-", "");
		boolean valid = ntsClient.isActiveBusiness(number);
		String formattedNumber = number.substring(0, 3) + "-" + number.substring(3, 5) + "-" + number.substring(5);
		String message = valid ? "확인 가능한 사업자등록번호입니다." : "확인할 수 없는 사업자등록번호입니다.";
		return new BusinessRegistrationVerifyResponse(formattedNumber, valid, message);
	}
}
