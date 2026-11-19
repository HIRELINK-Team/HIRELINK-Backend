package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegistrationStayDocumentResponse(
		@Schema(description = "PDF의 등록번호 일치 · 체류기간 확인 완료. 문서 진위 인증은 아님") boolean stayDocumentVerified,
		@Schema(description = "PDF에서 확인한 체류 만료일. 한국 날짜 기준 당일까지 유효") LocalDate stayExpiresOn,
		@Schema(description = "기존 가입 세션 만료 시각. PDF 제출로 연장하지 않음") Instant signupExpiresAt) {
}
