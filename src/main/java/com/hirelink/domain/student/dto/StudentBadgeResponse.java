package com.hirelink.domain.student.dto;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentBadgeResponse(
    @Schema(description="현재 유효 여부. 한국 날짜로 만료일 당일까지 true이며 조회 시 DB 회수 처리는 하지 않는다") boolean active,
    @Schema(description="마지막 발급 시각. 값이 있어도 현재 유효함을 뜻하지 않는다") Instant issuedAt,
    @Schema(description="최종 인증된 체류 만료일. 미확정 갱신 결과는 제외") LocalDate stayExpirationDate) { }
