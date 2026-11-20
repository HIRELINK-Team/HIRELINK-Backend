package com.hirelink.domain.member.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 회원의 위치 약관 동의 상태. 미저장은 false/null이며 조회만으로 동의 기록을 만들지 않는다.")
public record LocationConsentResponse(
		@Schema(description = "위치 약관 ID. 기존 PUT /api/users/me/term-agreements로 저장할 때 이 값을 사용하며 고정 ID를 가정하지 않는다", requiredMode = Schema.RequiredMode.REQUIRED)
		Long termTypeId,
		@Schema(description = "위치 약관 이름. 본문·번역은 프론트에서 제공", example = "위치기반 서비스 이용약관", requiredMode = Schema.RequiredMode.REQUIRED)
		String termTypeName,
		@Schema(description = "현재 동의 여부. true이면 위치 약관 동의창을 반복 표시하지 않는다. 기기의 GPS 접근 권한과는 별개", requiredMode = Schema.RequiredMode.REQUIRED)
		boolean isAgreed,
		@Schema(description = "현재 동의 시각. 미동의는 null, true 반복 저장은 기존 시각 유지, 철회 후 재동의하면 새 시각",
				type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Instant agreedAt) { }
