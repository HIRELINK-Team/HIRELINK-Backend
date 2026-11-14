package com.hirelink.domain.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.hirelink.domain.application.entity.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사업주용 지원 상세 정보")
public record EmployerApplicationDetailResponse(
		@Schema(description = "지원 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
		Long applicationId,
		@Schema(description = "채용공고 ID", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
		Long jobPostingId,
		@Schema(description = "지원 상태: WAITING_RESPONSE(사업주 응답 대기), OFFERED(채용 제안 발송), COMPLETED(채용 완료), ENDED(지원 종료)", example = "WAITING_RESPONSE", requiredMode = Schema.RequiredMode.REQUIRED)
		ApplicationStatus status,
		@Schema(description = "희망 근무 시작일", example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalDate desiredStartDate,
		@Schema(description = "지원 시각", example = "2026-11-14T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant appliedAt,
		@Schema(description = "사업주 응답 기한. 응답 단계가 끝난 경우 null", example = "2026-11-15T03:00:00Z", nullable = true)
		Instant employerResponseDeadline,
		@Schema(description = "지원 당시 유학생 정보", requiredMode = Schema.RequiredMode.REQUIRED)
		Applicant applicant,
		@Schema(description = "지원 당시 공고 요구사항", requiredMode = Schema.RequiredMode.REQUIRED)
		JobRequirements jobRequirements,
		@Schema(description = "지원 당시 매칭 판정", requiredMode = Schema.RequiredMode.REQUIRED)
		MatchResult matchResult) {

	@Schema(description = "지원 당시 유학생 정보")
	public record Applicant(
			@Schema(description = "이름", example = "김하이어", requiredMode = Schema.RequiredMode.REQUIRED)
			String name,
			@Schema(description = "프로필 이미지 임시 URL. 이미지가 없으면 null", example = "https://example.invalid/profile/sample.png", nullable = true)
			String profileImageUrl,
			@Schema(description = "국적", example = "VIETNAM", requiredMode = Schema.RequiredMode.REQUIRED)
			String nationality,
			@Schema(description = "체류 자격 코드", example = "D-2", requiredMode = Schema.RequiredMode.REQUIRED)
			String visaType,
			@Schema(description = "체류 기간 만료일", example = "2027-08-31", requiredMode = Schema.RequiredMode.REQUIRED)
			LocalDate visaExpirationDate,
			@Schema(description = "한국어 수준", example = "INTERMEDIATE", allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"}, requiredMode = Schema.RequiredMode.REQUIRED)
			String koreanLevel,
			@Schema(description = "TOPIK 등급. 미입력 시 null", example = "4", nullable = true)
			Short topikLevel,
			@Schema(description = "경력 목록", requiredMode = Schema.RequiredMode.REQUIRED)
			List<Career> careers,
			@Schema(description = "근무 가능 시간 목록", requiredMode = Schema.RequiredMode.REQUIRED)
			List<WorkTime> availableWorkTimes) {
	}

	@Schema(description = "지원 당시 경력")
	public record Career(
			@Schema(description = "경력명", example = "카페 아르바이트", requiredMode = Schema.RequiredMode.REQUIRED)
			String careerName,
			@Schema(description = "시작 월", example = "2025-03", requiredMode = Schema.RequiredMode.REQUIRED)
			String startMonth,
			@Schema(description = "종료 월", example = "2025-08", requiredMode = Schema.RequiredMode.REQUIRED)
			String endMonth,
			@Schema(description = "경력 설명. 미입력 시 null", example = "음료 제조와 고객 응대", nullable = true)
			String description) {
	}

	@Schema(description = "요일별 근무시간")
	public record WorkTime(
			@Schema(description = "요일", example = "MON", allowableValues = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}, requiredMode = Schema.RequiredMode.REQUIRED)
			String dayOfWeek,
			@Schema(description = "시작 시각", example = "09:00", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
			@JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@Schema(description = "종료 시각", example = "18:00", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
			@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
	}

	@Schema(description = "지원 당시 채용공고 요구사항")
	public record JobRequirements(
			@Schema(description = "공고 근무시간 목록", requiredMode = Schema.RequiredMode.REQUIRED)
			List<WorkTime> workTimes,
			@Schema(description = "급여 금액(원)", example = "12000", requiredMode = Schema.RequiredMode.REQUIRED)
			Integer wageAmount,
			@Schema(description = "요구 한국어 수준. 제한이 없으면 null", example = "INTERMEDIATE", allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"}, nullable = true)
			String requiredKoreanLevel) {
	}

	@Schema(description = "지원 당시 매칭 결과")
	public record MatchResult(
			@Schema(description = "매칭 점수", example = "92.50", requiredMode = Schema.RequiredMode.REQUIRED)
			BigDecimal matchScore,
			@Schema(description = "근무시간 완전 일치 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
			boolean workTimeMatched,
			@Schema(description = "경력 조건 충족 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
			boolean experienceMatched,
			@Schema(description = "희망 거리 완전 일치 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
			boolean locationMatched,
			@Schema(description = "희망 급여 완전 일치 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
			boolean wageMatched,
			@Schema(description = "한국어 수준 충족 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
			boolean koreanLevelMatched) {
	}
}
