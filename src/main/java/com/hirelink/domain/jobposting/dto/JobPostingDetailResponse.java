package com.hirelink.domain.jobposting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.jobposting.entity.JobPostingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용공고 상세 정보")
public record JobPostingDetailResponse(
		@Schema(description = "채용공고 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long jobPostingId,
		@Schema(description = "자동 생성 제목: 사업장명 + 직무 + 모집. 최대 200자, 넘으면 끝을 …로 축약. 임시저장 중 미입력은 null", maxLength = 200,
				example = "예시카페 바리스타 모집",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String title,
		@Schema(description = "공고 상태",
				allowableValues = {"DRAFT", "RECRUITING", "CLOSED"},
				example = "RECRUITING",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		JobPostingStatus status,
		@Schema(description = "사업장 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long workplaceId,
		@Schema(description = "사업장명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "예시카페", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String workplaceName,
		@Schema(description = "사업장 주소", maxLength = 500, example = "서울특별시 성동구 예시로 10", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String workplaceAddress,
		@Schema(description = "위도. 소수점 이하 최대 7자리", minimum = "-90", maximum = "90",
				example = "37.5665",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		BigDecimal latitude,
		@Schema(description = "경도. 소수점 이하 최대 7자리", minimum = "-180", maximum = "180",
				example = "126.978",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		BigDecimal longitude,
		@Schema(description = "업종 ID: 1 음식점·카페, 2 매장·판매, 3 사무·행사·관광, 4 통·번역, 5 면세점, 6 전공·학업, 7 인턴십, 8 기타. 임시저장 중 미입력은 null",
				example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Long businessTypeId,
		@Schema(description = "업종 코드 (한글 표시명이 아님). 임시저장 중 미입력은 null", example = "FOOD_CAFE", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String businessTypeName,
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
		@Schema(description = "채용 인원. 임시저장 중 미입력은 null", minimum = "1", maximum = "32767",
				example = "2",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Integer recruitmentCount,
		@Schema(description = "근무 시작일 (YYYY-MM-DD). 임시저장 중 미입력은 null",
				example = "2027-03-01", type = "string", format = "date",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		LocalDate workStartDate,
		@Schema(description = "요구 한국어 수준. 임시저장 중 미입력은 null. 값: BEGINNER / INTERMEDIATE / ADVANCED",
				pattern = "^(BEGINNER|INTERMEDIATE|ADVANCED)$",
				example = "INTERMEDIATE",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String requiredKoreanLevel,
		@Schema(description = "우대 경력. 임시저장 중 미입력은 null. 값: NONE / SIX_MONTHS_OR_MORE / ONE_YEAR_OR_MORE",
				pattern = "^(NONE|SIX_MONTHS_OR_MORE|ONE_YEAR_OR_MORE)$",
				example = "NONE",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String preferredExperience,
		@Schema(description = "근무시간 협의 가능 여부. 임시저장 중 미입력은 null", example = "false", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Boolean workTimeNegotiable,
		@Schema(description = "모집 마감일 (YYYY-MM-DD). 근무 시작일과의 선후 관계 제한 없음. 임시저장 중 미입력은 null",
				example = "2027-03-14", type = "string", format = "date",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		LocalDate applicationDeadlineDate,
		@Schema(description = "근무시간 목록. 월~일, 시작 시간 오름차순. 없으면 []", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<JobPostingWorkTimeResponse> workTimes) {
}
