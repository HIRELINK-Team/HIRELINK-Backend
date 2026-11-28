package com.hirelink.domain.student.dto;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="미리보기는 draftId, 최종 정보는 registeredAt을 제공한다. 원본 경로·직접 수정 입력은 제공하지 않는다.")
public record AcademicDocumentResponse(
    @Schema(description="서류 종류. 경로의 kind와 달리 대문자로 반환한다.", allowableValues={"TRANSCRIPT", "TOPIK"}, example="TRANSCRIPT")
    String documentType,
    @Schema(description="미리보기에서 발급한 초안 ID. 최종 등록·조회에서는 null이다.", format="uuid", nullable=true)
    UUID draftId,
    @Schema(description="종류별 OCR 추출 결과. 해당 종류에 사용하지 않는 필드는 null이다.")
    AcademicExtraction extracted,
    @Schema(description="최종 등록 시각(UTC). 미리보기에서는 null이다.", format="date-time", nullable=true, example="2026-11-29T03:00:00Z")
    Instant registeredAt) { }
