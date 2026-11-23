package com.hirelink.domain.student.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.hirelink.domain.student.entity.KoreanLevel;
import com.hirelink.domain.student.entity.StudyProgramType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유학생 프로필 상세 정보")
public record StudentProfileResponse(
		@Schema(description = "유학생 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long studentId,
		@Schema(description = "회원 이름", maxLength = 100, example = "김하늘", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String name,
		@Schema(description = "회원 이메일. 소셜 가입 시 제공되지 않으면 null", maxLength = 255, example = "student@example.com", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
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
		@Schema(description = "D-2는 저장된 학업과정으로 결정한 세부 코드. 갱신 시 D-10 등 지원 대상 밖의 체류자격도 원문으로 저장·조회한다",
				maxLength = 20,
				example = "D-2",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String visaCode,
		@Schema(description = "재학증명서 OCR로 확보하는 학교명. 직접 입력 불가, 기존 값 보존", maxLength = 150, example = "예시대학교", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		String universityName,
		@Schema(description = "학업 시작일 (YYYY-MM-DD)",
				example = "2025-03-01", type = "string", format = "date",
				requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		LocalDate enrollmentStartDate,
		@Schema(description = "학업과정 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Long studyProgramId,
		@Schema(description = "온보딩에서 선택한 학업과정. 기존 미입력 또는 지원 과정에 대응하지 않는 기존 데이터는 null", example = "BACHELOR", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		StudyProgramType studyProgram,
		@Schema(description = "재학증명서 OCR로 확보하는 학년. 직접 입력 불가, 기존 값 보존", example = "2", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
		Short gradeLevel,
		@Schema(description = "과거에 저장된 학적 상태. 신규 입력·수정은 받지 않으며 기존 값만 보존한다. 미입력은 null",
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
			@Schema(description = "저장된 근무 내용. 기존 경력에는 null 또는 빈 값이 남아 있을 수 있다. 경력 추가 · 수정 시에는 필수", example = "음료 제조 및 고객 응대", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
			String description) {
	}
}
