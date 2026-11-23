package com.hirelink.domain.student.dto;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩에서 제출하는 구직조건 전체. 기존 부분 수정 요청의 형식과 검증을 사용하되 필수 항목을 모두 제출한다.",
		requiredProperties = {"businessTypeFlexible", "preferredJobs", "preferredWorkTimes", "preferredLocation", "priorities"})
public class StudentOnboardingMatchingPreferenceRequest extends StudentMatchingPreferenceUpdateRequest {

	@Override
	@NotNull
	@Size(min = 1)
	@Schema(description = "연속 1시간 이상인 구간을 최소 하나 포함한다. 자정을 넘는 구간도 시작 요일 하나로 전송한다", requiredMode = Schema.RequiredMode.REQUIRED)
	public List<PreferredWorkTime> getPreferredWorkTimes() { return super.getPreferredWorkTimes(); }
}
