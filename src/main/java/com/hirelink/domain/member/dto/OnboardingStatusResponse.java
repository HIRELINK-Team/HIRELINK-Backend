package com.hirelink.domain.member.dto;

import java.time.Instant;
import com.hirelink.domain.member.entity.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원의 새 유학생 온보딩 상태. NOT_APPLICABLE은 기존 흐름을 사용한다는 뜻이며 기존 온보딩 완료 또는 서비스 이용 허가를 의미하지 않는다.")
public record OnboardingStatusResponse(
		@Schema(description = "로그인 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long userId,
		@Schema(description = "서버에 저장된 회원 유형", requiredMode = Schema.RequiredMode.REQUIRED) MemberRole role,
		@Schema(description = "REQUIRED: 새 유학생 온보딩 미완료, COMPLETED: 완료, NOT_APPLICABLE: 새 온보딩 적용 대상 아님", requiredMode = Schema.RequiredMode.REQUIRED) Status status,
		@Schema(description = "CURRENT_REGION: 생년월일을 보존한 회원은 거주지역부터 다시 입력, BASIC_INFORMATION: 이전 가입에서 생년월일이 보존되지 않은 회원은 생년월일부터 입력, NONE: 추가 온보딩 없음, EXISTING_FLOW: 기존 가입 유형별 흐름 사용", requiredMode = Schema.RequiredMode.REQUIRED) NextStep nextStep,
		@Schema(description = "저장된 최초 온보딩 완료 시각. 미완료 또는 적용 대상이 아니면 null", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant completedAt) {

	public enum Status { REQUIRED, COMPLETED, NOT_APPLICABLE }
	public enum NextStep { CURRENT_REGION, BASIC_INFORMATION, NONE, EXISTING_FLOW }
}
