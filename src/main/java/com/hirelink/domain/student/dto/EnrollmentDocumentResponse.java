package com.hirelink.domain.student.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record EnrollmentDocumentResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String universityName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String campusName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "4") short gradeLevel,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant registeredAt) { }
