package com.hirelink.domain.member.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 약관 메타데이터와 본인의 기존 동의 기록. 본문은 포함하지 않고 조회로 기록을 변경하지 않는다")
public record TermAgreementOverviewResponse(
		@Schema(description = "약관 ID 오름차순 목록", requiredMode = Schema.RequiredMode.REQUIRED) List<Term> terms) {
	@Schema(name = "TermAgreementOverviewTerm")
	public record Term(
			@Schema(description = "약관 종류 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long termTypeId,
			@Schema(description = "약관 이름", requiredMode = Schema.RequiredMode.REQUIRED) String termTypeName,
			@Schema(description = "필수 동의 항목 여부", requiredMode = Schema.RequiredMode.REQUIRED) boolean isRequired,
			@Schema(description = "현재 본문 버전. 미설정이면 null", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String currentVersion,
			@Schema(description = "본인의 동의 여부", requiredMode = Schema.RequiredMode.REQUIRED) boolean isAgreed,
			@Schema(description = "동의 시각. 미동의이면 null", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant agreedAt,
			@Schema(description = "기존 동의 버전. 과거 버전 미기록이면 null", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String agreedVersion) { }
}
