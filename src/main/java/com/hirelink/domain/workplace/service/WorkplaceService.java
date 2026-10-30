package com.hirelink.domain.workplace.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.workplace.dto.WorkplaceDetailResponse;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse.WorkplaceSummary;
import com.hirelink.domain.workplace.entity.Workplace;
import com.hirelink.domain.workplace.repository.WorkplaceRepository;
import com.hirelink.global.exception.WithdrawnAccountException;
import com.hirelink.global.exception.WorkplaceNotFoundException;

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
		validateEmployer(userId);
		List<WorkplaceSummary> workplaces = workplaceRepository.findAllByEmployerUserIdOrderByIdAsc(userId).stream()
				.map(workplace -> new WorkplaceSummary(workplace.getId(), workplace.getWorkplaceName(),
						workplace.getBusinessType().getId(), workplace.getBusinessType().getName(),
						workplace.getWorkplaceAddress(), workplace.getEmployeeCountRange()))
				.toList();
		return new WorkplaceListResponse(workplaces);
	}

	@Transactional(readOnly = true)
	public WorkplaceDetailResponse getWorkplace(Long userId, Long workplaceId) {
		validateEmployer(userId);
		Workplace workplace = workplaceRepository.findById(workplaceId)
				.orElseThrow(WorkplaceNotFoundException::new);
		if (!workplace.getEmployerUser().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 사업장만 조회할 수 있습니다.");
		}
		return new WorkplaceDetailResponse(workplace.getId(), workplace.getWorkplaceName(),
				workplace.getRepresentativeName(), formatBusinessRegistrationNumber(workplace.getBusinessRegistrationNumber()),
				workplace.getBusinessType().getId(), workplace.getBusinessType().getName(),
				workplace.getEmployeeCountRange(), workplace.getWorkplaceAddress(),
				workplace.getLatitude(), workplace.getLongitude());
	}

	private void validateEmployer(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 사업장을 조회할 수 있습니다.");
		}
	}

	private String formatBusinessRegistrationNumber(String number) {
		if (number.matches("[0-9]{3}-[0-9]{2}-[0-9]{5}")) {
			return number;
		}
		if (!number.matches("[0-9]{10}")) {
			throw new IllegalStateException("저장된 사업자등록번호 형식이 올바르지 않습니다.");
		}
		return number.substring(0, 3) + "-" + number.substring(3, 5) + "-" + number.substring(5);
	}
}
