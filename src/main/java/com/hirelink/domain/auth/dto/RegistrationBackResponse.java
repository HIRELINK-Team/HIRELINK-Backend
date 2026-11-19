package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "뒷면 인식 및 APICK 기재 정보 일치 확인 결과. 체류기간 PDF 확인과 휴대전화 가입의 필수 검증 연결은 후속 기능이다.")
public record RegistrationBackResponse(
		@Schema(description = "APICK 기재 정보 일치 확인 성공. 성공 응답에서 true", requiredMode = Schema.RequiredMode.REQUIRED) boolean cardVerified,
		@Schema(description = "뒷면에서 읽은 최신 체류 만료일", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate stayExpiresOn,
		@Schema(description = "한국 날짜 기준 만료일이 지났으면 true. 만료 당일은 false", requiredMode = Schema.RequiredMode.REQUIRED) boolean requiresStayDocument,
		@Schema(description = "기존 가입 세션 만료 시각", requiredMode = Schema.RequiredMode.REQUIRED) Instant signupExpiresAt) { }
