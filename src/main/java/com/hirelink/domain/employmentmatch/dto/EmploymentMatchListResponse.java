package com.hirelink.domain.employmentmatch.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "본인 채용 완료 목록")
public record EmploymentMatchListResponse(
		@Schema(description = "최신순 채용 완료 목록", requiredMode = Schema.RequiredMode.REQUIRED)
		List<Item> matches) {

	@Schema(description = "채용 완료 목록 항목")
	public record Item(
			@Schema(description = "채용 완료 ID", example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
			Long matchId,
			@Schema(description = "지원 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
			Long applicationId,
			@Schema(description = "채용 제안 ID", example = "401", requiredMode = Schema.RequiredMode.REQUIRED)
			Long offerId,
			@Schema(description = "채용공고 ID", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
			Long jobPostingId,
			@Schema(description = "채용공고 제목", example = "카페 주말 스태프", requiredMode = Schema.RequiredMode.REQUIRED)
			String jobTitle,
			@Schema(description = "사업장 이름", example = "하이어링크 카페", requiredMode = Schema.RequiredMode.REQUIRED)
			String workplaceName,
			@Schema(description = "급여 유형", example = "HOURLY", requiredMode = Schema.RequiredMode.REQUIRED)
			String wageType,
			@Schema(description = "급여 금액(원)", example = "12000", requiredMode = Schema.RequiredMode.REQUIRED)
			Integer wageAmount,
			@Schema(description = "희망 근무 시작일", example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED)
			LocalDate desiredStartDate,
			@Schema(description = "채용 완료 시각", example = "2026-11-14T05:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
			Instant matchedAt,
			@Schema(description = "유학생 정보. 사업주 조회 시에만 제공되고 유학생 조회 시 null", nullable = true)
			Student student) {
	}

	@Schema(description = "사업주에게 제공되는 유학생 요약")
	public record Student(
			@Schema(description = "유학생 이름", example = "김하이어", requiredMode = Schema.RequiredMode.REQUIRED)
			String name,
			@Schema(description = "프로필 이미지 임시 URL. 이미지가 없으면 null", example = "https://example.invalid/profile/sample.png", nullable = true)
			String profileImageUrl,
			@Schema(description = "국적", example = "VIETNAM", requiredMode = Schema.RequiredMode.REQUIRED)
			String nationality,
			@Schema(description = "체류 자격 코드", example = "D-2", requiredMode = Schema.RequiredMode.REQUIRED)
			String visaType,
			@Schema(description = "TOPIK 등급. 미입력 시 null", example = "4", nullable = true)
			Short topikLevel) {
	}
}
