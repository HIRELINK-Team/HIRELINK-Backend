package com.hirelink.domain.workplace.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse.WorkplaceSummary;
import com.hirelink.domain.workplace.repository.WorkplaceRepository;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class WorkplaceService {

	private final UserAccountRepository userAccountRepository;
	private final WorkplaceRepository workplaceRepository;

	public WorkplaceService(UserAccountRepository userAccountRepository, WorkplaceRepository workplaceRepository) {
		this.userAccountRepository = userAccountRepository;
		this.workplaceRepository = workplaceRepository;
	}

	@Transactional(readOnly = true)
	public WorkplaceListResponse getMyWorkplaces(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 사업장 목록을 조회할 수 있습니다.");
		}

		List<WorkplaceSummary> workplaces = workplaceRepository.findAllByEmployerUserIdOrderByIdAsc(userId).stream()
				.map(workplace -> new WorkplaceSummary(workplace.getId(), workplace.getWorkplaceName(),
						workplace.getBusinessType().getId(), workplace.getBusinessType().getName(),
						workplace.getWorkplaceAddress(), workplace.getEmployeeCountRange()))
				.toList();
		return new WorkplaceListResponse(workplaces);
	}
}
