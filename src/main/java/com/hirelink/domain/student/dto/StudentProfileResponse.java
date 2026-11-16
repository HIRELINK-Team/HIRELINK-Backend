package com.hirelink.domain.student.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.student.entity.KoreanLevel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유학생 프로필 상세 정보")
public record StudentProfileResponse(
		@Schema(description = "유학생 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long studentId,
		@Schema(description = "회원 이름", maxLength = 100, example = "김하늘", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String name,
		@Schema(description = "회원 이메일", maxLength = 255, example = "student@example.com", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String email,
		@Schema(description = "국내 010 휴대전화 번호. 입력 시 하이픈 생략 가능, 응답은 하이픈 포함",
				example = "010-1234-5678",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String phoneNumber,
		@Schema(description = "생년월일 (YYYY-MM-DD). 한국 기준 만 18세 이상",
				example = "2000-05-14", type = "string", format = "date",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		LocalDate birthDate,
		@Schema(description = "ISO 3166-1 alpha-2 국가 코드 (영문 대문자 2자리)",
				pattern = "[A-Z]{2}",
				example = "VN",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String nationality,
		@Schema(description = "현재 거주지역. 빈 문자열·공백만 입력 불가", maxLength = 150,
				example = "서울특별시 성동구",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String currentRegion,
		@Schema(description = "한국어 수준. 화면에서는 초급·중급·고급으로 변환 가능",
				allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"},
				example = "INTERMEDIATE",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		KoreanLevel koreanLevel,
		@Schema(description = "비자 코드. 값: D-2-1 / D-2-2 / D-2-3 / D-2-4 / D-2-6 / D-2-7 / D-2-8 / D-4-1 / D-4-7",
				pattern = "^(D-2-1|D-2-2|D-2-3|D-2-4|D-2-6|D-2-7|D-2-8|D-4-1|D-4-7)$",
				example = "D-2-2",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String visaCode,
		@Schema(description = "학교명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "예시대학교", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String universityName,
		@Schema(description = "학업 시작일 (YYYY-MM-DD)",
				example = "2025-03-01", type = "string", format = "date",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		LocalDate enrollmentStartDate,
		@Schema(description = "학업과정 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Long studyProgramId,
		@Schema(description = "학년. 이번 프로필 수정 API에서는 변경하지 않음", example = "2", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Short gradeLevel,
		@Schema(description = "학업 상태. D-2-1/2/3/4: ENROLLED, ON_LEAVE, COMPLETED, GRADUATED; D-2-6/8, D-4-1/7: IN_PROGRESS, COMPLETED, DISCONTINUED; D-2-7: IN_PROGRESS, ON_LEAVE_OR_DISCONTINUED, COMPLETED",
				pattern = "^(ENROLLED|ON_LEAVE|COMPLETED|GRADUATED|IN_PROGRESS|DISCONTINUED|ON_LEAVE_OR_DISCONTINUED)$",
				example = "ENROLLED",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String academicState,
		@Schema(description = "TOPIK 급수. 미등록은 null", minimum = "1", maximum = "6",
				example = "4",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Short topikLevel,
		@Schema(description = "체류 시작일 (YYYY-MM-DD)",
				example = "2025-03-01", type = "string", format = "date",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		LocalDate stayStartDate,
		@Schema(description = "체류 만료일 (YYYY-MM-DD). 과거 날짜 허용, 기존 체류 시작일보다 앞설 수 없음",
				example = "2027-03-01", type = "string", format = "date",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		LocalDate stayExpirationDate,
		@Schema(description = "프로필 사진 임시 URL. 사진 미등록 시 null",
				example = "https://example.com/profile.jpg",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String profileImageUrl,
		@Schema(description = "배지 발급 시각 (ISO-8601). 미발급 시 null",
				example = "2026-11-01T01:00:00Z", type = "string", format = "date-time",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Instant hirelinkBadgeIssuedAt,
		@Schema(description = "경력 목록. 시작월 내림차순, 같은 시작월은 ID 내림차순. 없으면 []", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		List<Career> careers) {

	@Schema(name = "StudentProfileResponseCareer")
	public record Career(
			@Schema(description = "경력 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			Long careerId,
			@Schema(description = "경력명. 빈 문자열·공백만 입력 불가", maxLength = 150, example = "카페 아르바이트", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String careerName,
			@Schema(description = "근무 시작월 (YYYY-MM). 한국 기준 이번 달까지 허용",
					pattern = "[0-9]{4}-(0[1-9]|1[0-2])",
					example = "2026-01", type = "string",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String startMonth,
			@Schema(description = "근무 종료월 (YYYY-MM). 시작월 이상, 한국 기준 이번 달까지 허용",
					pattern = "[0-9]{4}-(0[1-9]|1[0-2])",
					example = "2026-06", type = "string",
					requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
			String endMonth,
			@Schema(description = "근무 내용. null 또는 빈 문자열 허용", example = "음료 제조 및 고객 응대", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
			String description) {
	}
}
