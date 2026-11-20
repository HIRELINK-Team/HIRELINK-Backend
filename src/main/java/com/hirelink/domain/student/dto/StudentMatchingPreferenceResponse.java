package com.hirelink.domain.student.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유학생 매칭 조건")
public record StudentMatchingPreferenceResponse(
		@Schema(description = "업종 상관없음 여부. 미설정 시 false", example = "false", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		boolean businessTypeFlexible,
		@Schema(description = "희망 업종 목록. 업종 ID 오름차순, 미설정이면 []", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<PreferredJob> preferredJobs,
		@Schema(description = "희망 근무시간 목록. 월~일·시작 시간 오름차순, 미설정이면 []", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<PreferredWorkTime> preferredWorkTimes,
		@Schema(description = "희망 근무지역. 미설정이면 null", requiredMode = Schema.RequiredMode.REQUIRED,
				types = {"object", "null"}, schemaResolution = Schema.SchemaResolution.INLINE)
		PreferredLocation preferredLocation,
		@Schema(description = "희망 최소 시급 (원). 1 이상의 정수, null은 미설정", minimum = "1",
				example = "12000",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Integer minimumHourlyWage,
		@Schema(description = "우선순위 목록. 1순위부터 반환, 미설정이면 []", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<Priority> priorities) {

	@Schema(name = "StudentMatchingPreferenceResponsePreferredJob")
	public record PreferredJob(
			@Schema(description = "업종 ID: 1 음식점·카페, 2 매장·판매, 3 사무·행사·관광, 4 통·번역, 5 면세점, 6 전공·학업, 7 인턴십, 8 기타",
					example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long businessTypeId,
			@Schema(description = "업종 코드 (한글 표시명이 아님)", example = "FOOD_CAFE", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String businessTypeName,
			@Schema(description = "OTHER 업종의 기타 업종명. 다른 업종에서는 null", maxLength = 100,
					example = "영화관",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
			String otherJobName) {
	}

	@Schema(name = "StudentMatchingPreferenceResponsePreferredWorkTime")
	public record PreferredWorkTime(
			@Schema(description = "요일",
					allowableValues = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"},
					example = "MON",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String dayOfWeek,
			@JsonFormat(pattern = "HH:mm")
			@Schema(description = "시작 시간 (HH:mm), 30분 단위",
					pattern = "([01][0-9]|2[0-3]):(00|30)",
					example = "09:00", type = "string", format = "HH:mm",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			LocalTime startTime,
			@JsonFormat(pattern = "HH:mm")
			@Schema(description = "종료 시간 (HH:mm), 30분 단위. 00:00은 해당 요일의 끝인 자정. 시작과 같을 수 없음",
					pattern = "([01][0-9]|2[0-3]):(00|30)",
					example = "13:00", type = "string", format = "HH:mm",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			LocalTime endTime) {
	}

	@Schema(name = "StudentMatchingPreferenceResponsePreferredLocation")
	public record PreferredLocation(
			@Schema(description = "희망 지역명. 빈 문자열·공백만 입력 불가", maxLength = 150,
					example = "서울특별시 성동구",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String locationName,
			@Schema(description = "위도. 소수점 이하 최대 7자리", minimum = "-90", maximum = "90",
					example = "37.5665",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			BigDecimal latitude,
			@Schema(description = "경도. 소수점 이하 최대 7자리", minimum = "-180", maximum = "180",
					example = "126.978",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			BigDecimal longitude,
			@Schema(description = "저장된 희망 근무 반경 (미터). 새 입력은 100~1,000m의 100m 간격이며 기존 저장값은 그대로 반환한다", minimum = "1", example = "500", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Integer radiusM) {
	}

	@Schema(name = "StudentMatchingPreferenceResponsePriority")
	public record Priority(
			@Schema(description = "우선순위 기준 ID: 1 근무시간, 2 시급, 3 거리, 4 업종", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long criterionId,
			@Schema(description = "우선순위 기준 코드",
					allowableValues = {"WORK_TIME", "WAGE", "DISTANCE", "BUSINESS_TYPE"},
					example = "WORK_TIME",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String criterionName,
			@Schema(description = "순위. 1~4를 각각 한 번 사용", minimum = "1", maximum = "4",
					example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Short priorityOrder) {
	}
}
