package com.hirelink.domain.workplace.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.exception.ConstraintViolationException;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.workplace.dto.WorkplaceDetailResponse;
import com.hirelink.domain.workplace.dto.WorkplaceCreateRequest;
import com.hirelink.domain.workplace.client.NtsBusinessRegistrationClient;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse;
import com.hirelink.domain.workplace.dto.WorkplaceListResponse.WorkplaceSummary;
import com.hirelink.domain.workplace.dto.WorkplaceUpdateRequest;
import com.hirelink.domain.workplace.entity.BusinessType;
import com.hirelink.domain.workplace.entity.Workplace;
import com.hirelink.domain.workplace.repository.BusinessTypeRepository;
import com.hirelink.domain.workplace.repository.WorkplaceRepository;
import com.hirelink.global.exception.BusinessTypeNotFoundException;
import com.hirelink.global.exception.DuplicateBusinessRegistrationException;
import com.hirelink.global.exception.InvalidWorkplaceException;
import com.hirelink.global.exception.WithdrawnAccountException;
import com.hirelink.global.exception.WorkplaceNotFoundException;

@Service
public class WorkplaceService {

	private final UserAccountRepository userAccountRepository;
	private final WorkplaceRepository workplaceRepository;
	private final BusinessTypeRepository businessTypeRepository;
	private final NtsBusinessRegistrationClient ntsClient;

	public WorkplaceService(UserAccountRepository userAccountRepository, WorkplaceRepository workplaceRepository,
			BusinessTypeRepository businessTypeRepository, NtsBusinessRegistrationClient ntsClient) {
		this.userAccountRepository = userAccountRepository;
		this.workplaceRepository = workplaceRepository;
		this.businessTypeRepository = businessTypeRepository;
		this.ntsClient = ntsClient;
	}

	@Transactional
	public WorkplaceDetailResponse createWorkplace(Long userId, WorkplaceCreateRequest request) {
		UserAccount user = validateEmployer(userId);
		String number = request.getBusinessRegistrationNumber().replace("-", "");
		if (workplaceRepository.existsByBusinessRegistrationNumberIn(List.of(number, formatBusinessRegistrationNumber(number)))) {
			throw new DuplicateBusinessRegistrationException();
		}
		BusinessType businessType = businessTypeRepository.findById(request.getBusinessTypeId())
				.orElseThrow(BusinessTypeNotFoundException::new);
		if (!ntsClient.isActiveBusiness(number)) {
			throw new InvalidWorkplaceException("현재 영업 중인 사업자등록번호만 등록할 수 있습니다.");
		}
		Workplace workplace = new Workplace(user, request.getWorkplaceName(), null,
				null, number, businessType, request.getWorkplaceAddress(), request.getLatitude(), request.getLongitude(),
				request.getEmployeeCountRange());
		try {
			workplaceRepository.saveAndFlush(workplace);
		} catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof ConstraintViolationException violation
						&& "uk_workplace_business_registration_number".equals(violation.getConstraintName())) {
					throw new DuplicateBusinessRegistrationException();
				}
			}
			throw new IllegalStateException("사업장 저장에 실패했습니다.");
		}
		return toDetailResponse(workplace);
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
		return toDetailResponse(workplace);
	}

	@Transactional
	public WorkplaceDetailResponse updateWorkplace(Long userId, Long workplaceId, WorkplaceUpdateRequest request) {
		validateEmployer(userId);
		if (!request.hasChanges()) {
			throw new InvalidWorkplaceException("수정할 항목을 하나 이상 입력해주세요.");
		}
		if (request.hasLocationChanges()
				&& (request.getWorkplaceAddress() == null || request.getLatitude() == null || request.getLongitude() == null)) {
			throw new InvalidWorkplaceException("주소와 위도·경도를 함께 입력해주세요.");
		}

		Workplace workplace = workplaceRepository.findByIdForUpdate(workplaceId)
				.orElseThrow(WorkplaceNotFoundException::new);
		if (!workplace.getEmployerUser().getId().equals(userId)) {
			throw new AccessDeniedException("본인의 사업장만 수정할 수 있습니다.");
		}
		BusinessType businessType = request.getBusinessTypeId() == null ? null
				: businessTypeRepository.findById(request.getBusinessTypeId()).orElseThrow(BusinessTypeNotFoundException::new);

		if (request.getWorkplaceName() != null) {
			workplace.updateName(request.getWorkplaceName());
		}
		if (businessType != null) {
			workplace.updateBusinessType(businessType);
		}
		if (request.hasLocationChanges()) {
			workplace.updateLocation(request.getWorkplaceAddress(), request.getLatitude(), request.getLongitude());
		}
		return toDetailResponse(workplace);
	}

	private UserAccount validateEmployer(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 사업장을 관리할 수 있습니다.");
		}
		return user;
	}

	private WorkplaceDetailResponse toDetailResponse(Workplace workplace) {
		return new WorkplaceDetailResponse(workplace.getId(), workplace.getWorkplaceName(),
				formatBusinessRegistrationNumber(workplace.getBusinessRegistrationNumber()),
				workplace.getBusinessType().getId(), workplace.getBusinessType().getName(),
				workplace.getEmployeeCountRange(), workplace.getWorkplaceAddress(),
				workplace.getLatitude(), workplace.getLongitude());
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
