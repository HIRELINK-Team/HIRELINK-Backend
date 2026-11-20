package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카드 뒷면·체류기간 PDF 확인 공통 결과. 일련번호는 항상 카드 뒷면에서 읽은 값이며 허가일자·신고일은 반환하지 않는다. 계정 생성·온보딩 완료를 의미하지 않는다.")
public record RegistrationVerificationResponse(
		@Schema(description = "카드 뒷면 일련번호. 선행 0을 유지하는 11자리 문자열. 2011-01-01 이전 발급 카드에서 일련번호가 없으면 null",
				requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}, nullable = true,
				minLength = 11, maxLength = 11, pattern = "^[0-9]{11}$", example = "00000000001") String serialNumber,
		@Schema(description = "뒷면 응답은 카드의 최신 체류 만료일, PDF 응답은 PDF로 확인한 유효한 만료일. requiresStayDocument=true이면 PDF 확인이 아직 필요하다",
				requiredMode = Schema.RequiredMode.REQUIRED, example = "2027-07-27") LocalDate stayExpiresOn,
		@Schema(description = "APICK 카드 기재 정보 일치 확인 완료. 두 API의 성공 응답에서 true",
				requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean cardVerified,
		@Schema(description = "PDF의 등록번호 일치·체류기간 확인 완료. 뒷면 응답은 false, PDF 성공 응답은 true. 문서 진위 인증은 아님",
				requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean stayDocumentVerified,
		@Schema(description = "추가 PDF 확인 필요 여부. 뒷면 만료일이 한국 날짜 기준 지났으면 true, 만료 당일과 PDF 성공 응답은 false",
				requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean requiresStayDocument,
		@Schema(description = "기존 가입 세션 만료 시각. 뒷면·PDF 요청으로 연장하지 않음",
				requiredMode = Schema.RequiredMode.REQUIRED) Instant signupExpiresAt) {
	@Override public String toString() { return "RegistrationVerificationResponse[redacted]"; }
}
