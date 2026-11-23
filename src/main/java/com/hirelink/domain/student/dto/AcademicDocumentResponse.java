package com.hirelink.domain.student.dto;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="미리보기는 draftId, 최종 정보는 registeredAt을 제공한다. 원본 경로·직접 수정 입력은 제공하지 않는다.")
public record AcademicDocumentResponse(String documentType, UUID draftId, AcademicExtraction extracted, Instant registeredAt) { }
