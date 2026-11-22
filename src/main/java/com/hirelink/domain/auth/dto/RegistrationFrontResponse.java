package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "등록증 앞면 인식 결과. 확인용 등록번호 전체를 반환한다. 직접 수정할 수 없으며 오류가 있으면 앞면을 재촬영한다. 화면 이탈·인증 완료 시 프론트에서 확인용 원문 상태를 정리한다.")
public record RegistrationFrontResponse(
		@Schema(description = "인식한 이름", requiredMode = Schema.RequiredMode.REQUIRED, example = "TEST STUDENT") String name,
		@Schema(description = "ISO 국가 코드", requiredMode = Schema.RequiredMode.REQUIRED, example = "VN") String nationalityCode,
		@Schema(description = "인식한 체류자격. D-2 또는 D-2-1·D-2-2·D-2-3·D-2-4·D-2-6. D-2는 온보딩 학업과정 선택으로 세분화", requiredMode = Schema.RequiredMode.REQUIRED, example = "D-2") String visaCode,
		@Schema(description = "인식한 외국인등록번호 전체. 숫자 6자리-7자리, 확인 화면에서만 사용", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false,
				minLength = 14, maxLength = 14, pattern = "^[0-9]{6}-[5-8][0-9]{6}$", example = "000101-5000000") String registrationNumber,
		@Schema(description = "발급일", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate issueDate,
		@Schema(description = "기존 가입 세션 만료 시각. 요청으로 연장되지 않음", requiredMode = Schema.RequiredMode.REQUIRED) Instant signupExpiresAt) {
	@Override public String toString() { return "RegistrationFrontResponse[redacted]"; }
}
