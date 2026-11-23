package com.hirelink.domain.student.dto;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="만료일별 사전 안내 표시권 획득 결과. show=true인 응답에서만 화면을 표시한다")
public record StudentStayExpirationNoticeResponse(
        @Schema(description="이번 요청에서 최초 안내 대상으로 기록되었는지 여부") boolean show,
        @Schema(description="현재 최종 확인된 만료일. 인증 정보가 없으면 null") LocalDate stayExpirationDate,
        @Schema(description="한국 날짜 기준 이번 요청 시점의 남은 일수. 인증 정보가 없으면 null") Long remainingDays,
        @Schema(description="show=true일 때 안내 문구. 그 외에는 null") String message) { }
