package com.hirelink.domain.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.hirelink.domain.application.entity.ApplicationEndReason;
import com.hirelink.domain.application.entity.ApplicationStatus;
import com.hirelink.domain.employmentoffer.entity.EmploymentOfferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유학생용 지원 상세 정보")
public record StudentApplicationDetailResponse(
		@Schema(description = "지원 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
		Long applicationId,
		@Schema(description = "지원 상태: WAITING_RESPONSE(사업주 응답 대기), OFFERED(채용 제안 발송), COMPLETED(채용 완료), ENDED(지원 종료)", example = "OFFERED", requiredMode = Schema.RequiredMode.REQUIRED)
		ApplicationStatus status,
		@Schema(description = "채용공고 ID", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
		Long jobPostingId,
		@Schema(description = "채용공고 제목", example = "카페 주말 스태프", requiredMode = Schema.RequiredMode.REQUIRED)
		String jobTitle,
		@Schema(description = "사업장 이름", example = "하이어링크 카페", requiredMode = Schema.RequiredMode.REQUIRED)
		String workplaceName,
		@Schema(description = "사업장 주소", example = "서울특별시 중구 세종대로 1", requiredMode = Schema.RequiredMode.REQUIRED)
		String workplaceAddress,
		@Schema(description = "급여 유형", example = "HOURLY", requiredMode = Schema.RequiredMode.REQUIRED)
		String wageType,
		@Schema(description = "급여 금액(원)", example = "12000", requiredMode = Schema.RequiredMode.REQUIRED)
		Integer wageAmount,
		@Schema(description = "근무시간 목록", requiredMode = Schema.RequiredMode.REQUIRED)
		List<WorkTime> workTimes,
		@Schema(description = "희망 근무 시작일", example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalDate desiredStartDate,
		@Schema(description = "지원 시각", example = "2026-11-14T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant appliedAt,
		@Schema(description = "사업주 응답 기한. 응답 단계 종료 후 null", example = "2026-11-15T03:00:00Z", nullable = true)
		Instant employerResponseDeadline,
		@Schema(description = "채용 제안 ID. 제안이 없으면 null", example = "401", nullable = true)
		Long offerId,
		@Schema(description = "채용 제안 상태: PENDING(응답 대기), ACCEPTED(수락), REJECTED(거절), EXPIRED(응답 만료). 제안이 없으면 null", example = "PENDING", nullable = true)
		EmploymentOfferStatus offerStatus,
		@Schema(description = "채용 제안 시각. 제안이 없으면 null", example = "2026-11-15T03:00:00Z", nullable = true)
		Instant offeredAt,
		@Schema(description = "유학생 응답 기한. 제안이 없으면 null", example = "2026-11-16T03:00:00Z", nullable = true)
		Instant studentResponseDeadline,
		@Schema(description = "제안 응답 시각. 아직 응답하지 않았으면 null", example = "2026-11-15T05:00:00Z", nullable = true)
		Instant respondedAt,
		@Schema(description = "채용 완료 ID. 완료되지 않았으면 null", example = "501", nullable = true)
		Long matchId,
		@Schema(description = "지원 종료 사유: EMPLOYER_REJECTED(사업주 거절), WITHDRAWN(유학생 취소), EMPLOYER_RESPONSE_EXPIRED(사업주 응답 만료), STUDENT_REJECTED(유학생 제안 거절), STUDENT_RESPONSE_EXPIRED(유학생 응답 만료), JOB_POSTING_CLOSED(공고 마감). ENDED 상태가 아니면 null", example = "STUDENT_REJECTED", nullable = true)
		ApplicationEndReason endReason,
		@Schema(description = "지원 종료 시각. ENDED 상태가 아니면 null", example = "2026-11-15T05:00:00Z", nullable = true)
		Instant endedAt) {

	@Schema(description = "요일별 근무시간")
	public record WorkTime(
			@Schema(description = "요일", example = "SAT", allowableValues = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}, requiredMode = Schema.RequiredMode.REQUIRED)
			String dayOfWeek,
			@Schema(description = "근무 시작 시각", example = "10:00", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
			@JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@Schema(description = "근무 종료 시각", example = "18:00", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
			@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
	}
}
