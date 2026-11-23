package com.hirelink.domain.employmentmatch.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공고에서 삭제할 채용 완료 선택 목록", example = "{\"matchIds\":[501,502]}")
public record EmploymentMatchDeleteRequest(
		@NotEmpty(message = "삭제할 채용 완료를 선택해주세요.")
		@Schema(description = "현재 선택한 공고에 속한 채용 완료 ID 목록. 이미 삭제된 ID의 재요청은 성공 처리합니다.",
				requiredMode = Schema.RequiredMode.REQUIRED)
		List<@NotNull(message = "채용 완료 ID는 null일 수 없습니다.")
				@Positive(message = "채용 완료 ID는 양수여야 합니다.") Long> matchIds) {
}
