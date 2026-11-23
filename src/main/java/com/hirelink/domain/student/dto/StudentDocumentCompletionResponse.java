package com.hirelink.domain.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최종 등록된 서류별 반영 여부와 완성률. 미확정 추출 결과는 포함하지 않는다")
public record StudentDocumentCompletionResponse(
        @Schema(description = "각 항목당 25%의 합계", allowableValues = {"0", "25", "50", "75", "100"}, example = "75")
        int completionPercentage,
        @Schema(description = "등록증 인증 완료 및 한국 날짜 기준 최종 체류 만료일 당일까지 true")
        boolean registrationIncluded,
        @Schema(description = "재학증명서 최종 등록 완료 시 true") boolean enrollmentIncluded,
        @Schema(description = "성적증명서 최종 등록 완료 시 true") boolean transcriptIncluded,
        @Schema(description = "TOPIK 성적증명서 최종 등록 완료 시 true") boolean topikIncluded) { }
