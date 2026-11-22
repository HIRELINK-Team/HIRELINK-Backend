package com.hirelink.domain.student.dto;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record EnrollmentPreviewResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "본인 최신 추출 결과의 최종 확인 ID") UUID draftId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String universityName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String campusName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "4", description = "학년 우선, 없으면 개인 학기 수/2 올림") short gradeLevel,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "서버가 보관한 원본 추출값. 사용자가 수정할 수 없음") EnrollmentExtraction extracted) { }
