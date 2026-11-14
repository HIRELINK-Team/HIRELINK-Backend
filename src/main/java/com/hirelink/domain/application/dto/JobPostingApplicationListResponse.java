package com.hirelink.domain.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.application.entity.ApplicationEndReason;
import com.hirelink.domain.application.entity.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용공고별 지원 목록과 상태 집계")
public record JobPostingApplicationListResponse(
		@Schema(description = "채용공고 ID", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
		Long jobPostingId,
		@Schema(description = "동시에 검토 가능한 최대 지원 수", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
		int capacity,
		@Schema(description = "현재 검토 중인 지원 수", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
		long occupiedCount,
		@Schema(description = "사업주 응답 대기 지원 수", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		long waitingResponseCount,
		@Schema(description = "채용 제안 발송 지원 수", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		long offeredCount,
		@Schema(description = "채용 완료 지원 수", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
		long completedCount,
		@Schema(description = "지원 목록", requiredMode = Schema.RequiredMode.REQUIRED)
		List<Item> applications) {

	@Schema(description = "공고별 지원 목록 항목")
	public record Item(
			@Schema(description = "지원 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
			Long applicationId,
			@Schema(description = "지원 상태: WAITING_RESPONSE(사업주 응답 대기), OFFERED(채용 제안 발송), COMPLETED(채용 완료), ENDED(지원 종료)", example = "WAITING_RESPONSE", requiredMode = Schema.RequiredMode.REQUIRED)
			ApplicationStatus status,
			@Schema(description = "지원자 이름", example = "김하이어", requiredMode = Schema.RequiredMode.REQUIRED)
			String applicantName,
			@Schema(description = "프로필 이미지 임시 URL. 이미지가 없으면 null", example = "https://example.invalid/profile/sample.png", nullable = true)
			String profileImageUrl,
			@Schema(description = "국적", example = "VIETNAM", requiredMode = Schema.RequiredMode.REQUIRED)
			String nationality,
			@Schema(description = "체류 자격 코드", example = "D-2", requiredMode = Schema.RequiredMode.REQUIRED)
			String visaType,
			@Schema(description = "TOPIK 등급. 미입력 시 null", example = "4", nullable = true)
			Short topikLevel,
			@Schema(description = "매칭 점수", example = "92.50", requiredMode = Schema.RequiredMode.REQUIRED)
			BigDecimal matchScore,
			@Schema(description = "희망 근무 시작일", example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED)
			LocalDate desiredStartDate,
			@Schema(description = "지원 시각", example = "2026-11-14T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
			Instant appliedAt,
			@Schema(description = "사업주 응답 기한. 응답 단계 종료 후 null", example = "2026-11-15T03:00:00Z", nullable = true)
			Instant employerResponseDeadline,
			@Schema(description = "채용 제안 ID. 제안이 없으면 null", example = "401", nullable = true)
			Long offerId,
			@Schema(description = "유학생 응답 기한. 제안이 없으면 null", example = "2026-11-16T03:00:00Z", nullable = true)
			Instant studentResponseDeadline,
			@Schema(description = "채용 완료 ID. 완료되지 않았으면 null", example = "501", nullable = true)
			Long matchId,
			@Schema(description = "지원 종료 사유: EMPLOYER_REJECTED(사업주 거절), WITHDRAWN(유학생 취소), EMPLOYER_RESPONSE_EXPIRED(사업주 응답 만료), STUDENT_REJECTED(유학생 제안 거절), STUDENT_RESPONSE_EXPIRED(유학생 응답 만료), JOB_POSTING_CLOSED(공고 마감). ENDED 상태가 아니면 null", example = "EMPLOYER_REJECTED", nullable = true)
			ApplicationEndReason endReason,
			@Schema(description = "지원 종료 시각. ENDED 상태가 아니면 null", example = "2026-11-14T05:00:00Z", nullable = true)
			Instant endedAt) {
	}
}
