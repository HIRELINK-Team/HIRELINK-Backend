package com.hirelink.domain.member.dto;

import java.time.Instant;
import java.util.List;

public record TermAgreementResponse(List<Agreement> agreements) {

	public record Agreement(Long termTypeId, boolean isAgreed, Instant agreedAt) {
	}
}
