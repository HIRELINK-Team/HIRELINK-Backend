package com.hirelink.domain.member.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TermAgreementRequest(
		@NotNull(message = "약관 동의 목록을 입력해주세요.")
		@Valid List<@NotNull(message = "약관 동의 항목은 null일 수 없습니다.") Agreement> agreements) {

	public record Agreement(
			@NotNull(message = "약관 ID를 입력해주세요.")
			@Positive(message = "약관 ID는 양수여야 합니다.") Long termTypeId,
			@NotNull(message = "약관 동의 여부를 입력해주세요.") Boolean isAgreed) {
	}
}
