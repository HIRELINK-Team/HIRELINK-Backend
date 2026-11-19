package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "등록증 앞면 인식 결과. 등록번호 뒷자리 7개는 숨긴다. 직접 수정할 수 없으며 오류가 있으면 앞면을 재촬영한다.")
public record RegistrationFrontResponse(
		@Schema(description = "인식한 이름", requiredMode = Schema.RequiredMode.REQUIRED, example = "TEST STUDENT") String name,
		@Schema(description = "ISO 국가 코드", requiredMode = Schema.RequiredMode.REQUIRED, example = "VN") String nationalityCode,
		@Schema(description = "인식한 체류자격. 기존 지원 코드 범위", requiredMode = Schema.RequiredMode.REQUIRED, example = "D-2") String visaCode,
		@Schema(description = "뒷자리를 가린 외국인등록번호", requiredMode = Schema.RequiredMode.REQUIRED, example = "000101-*******") String maskedRegistrationNumber,
		@Schema(description = "발급일", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate issueDate,
		@Schema(description = "기존 가입 세션 만료 시각. 요청으로 연장되지 않음", requiredMode = Schema.RequiredMode.REQUIRED) Instant signupExpiresAt) {
	@Override public String toString() { return "RegistrationFrontResponse[redacted]"; }
}
