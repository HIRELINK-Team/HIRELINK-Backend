package com.hirelink.domain.developerinquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개발자 문의 접수 요청")
public record DeveloperInquiryCreateRequest(
		@NotBlank(message = "문의 유형을 입력해주세요.")
		@Size(max = 50, message = "문의 유형은 50자 이하여야 합니다.")
		@Schema(description = "문의 유형. 전체 선택지는 미확정이며 현재 확인된 화면 표시값은 '사용 방법'",
				example = "사용 방법", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
		String inquiryType,
		@NotBlank(message = "문의 제목을 입력해주세요.")
		@Size(max = 200, message = "문의 제목은 200자 이하여야 합니다.")
		@Schema(description = "문의 제목", example = "채용 제안 확인 방법을 알고 싶어요", maxLength = 200,
				requiredMode = Schema.RequiredMode.REQUIRED)
		String title,
		@NotBlank(message = "문의 내용을 입력해주세요.")
		@Size(max = 5000, message = "문의 내용은 5,000자 이하여야 합니다.")
		@Schema(description = "문의 내용", example = "받은 채용 제안은 어디에서 확인할 수 있나요?", maxLength = 5000,
				requiredMode = Schema.RequiredMode.REQUIRED)
		String content) {
}
