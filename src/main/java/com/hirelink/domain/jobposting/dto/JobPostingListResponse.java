package com.hirelink.domain.jobposting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 채용공고 목록")
public record JobPostingListResponse(
		@Schema(description = "공고 목록. 생성 시각 내림차순, 같으면 ID 내림차순. 없으면 []", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<JobPostingSummary> jobPostings) {

	@Schema(name = "JobPostingListResponseJobPostingSummary")
	public record JobPostingSummary(
			@Schema(description = "채용공고 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long jobPostingId,
			@Schema(description = "사업장 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long workplaceId,
			@Schema(description = "사업장명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "예시카페", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String workplaceName,
			@Schema(description = "직무. 빈 문자열·공백만 입력 불가. 임시저장 중 미입력은 null", maxLength = 100,
					example = "바리스타",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
			String jobRole,
			@Schema(description = "급여 유형. 임시저장 중 미입력은 null. 값: HOURLY / MONTHLY",
					pattern = "^(HOURLY|MONTHLY)$",
					example = "HOURLY",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
			String wageType,
			@Schema(description = "급여 금액 (원), 1 이상의 정수. 임시저장 중 미입력은 null", minimum = "1",
					example = "13000",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
			Integer wageAmount,
			@Schema(description = "공고 상태",
					allowableValues = {"DRAFT", "RECRUITING", "CLOSED"},
					example = "RECRUITING",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			JobPostingStatus status,
			@Schema(description = "모집 마감일 (YYYY-MM-DD). 근무 시작일과의 선후 관계 제한 없음. 임시저장 중 미입력은 null",
					example = "2027-03-14", type = "string", format = "date",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
			LocalDate applicationDeadlineDate,
			@Schema(description = "근무시간 목록. 월~일, 시작 시간 오름차순. 없으면 []", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			List<JobPostingWorkTimeResponse> workTimes,
			@Schema(description = "생성 시각 (ISO-8601)",
					example = "2026-11-01T01:00:00Z", type = "string", format = "date-time",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Instant createdAt) {
	}
}
