package com.hirelink.domain.student.dto;

import java.time.LocalDate;

import com.hirelink.domain.student.entity.KoreanLevel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유학생 프로필 등록 결과")
public record StudentProfileCreateResponse(
		@Schema(description = "유학생 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		Long studentId,
		@Schema(description = "회원 이름", maxLength = 100, example = "김하늘", requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
		String name,
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
		KoreanLevel koreanLevel) {
}
