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
	@Schema(description = "최소 1개 희망 근무시간. 자정을 넘는 구간은 요일별로 나눠 전송한다", requiredMode = Schema.RequiredMode.REQUIRED)
	public List<PreferredWorkTime> getPreferredWorkTimes() { return super.getPreferredWorkTimes(); }
}
