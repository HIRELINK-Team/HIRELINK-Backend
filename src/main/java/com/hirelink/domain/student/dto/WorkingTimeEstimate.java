package com.hirelink.domain.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "서비스의 예상 근무시간 기준이며 시간제취업 허가 완료를 뜻하지 않는다")
public record WorkingTimeEstimate(
    int requiredTopikLevel,
    boolean topikRequirementMet,
    boolean accreditedUniversity,
    @Schema(description = "직전 학기 평균 평점 자체가 4.0 이상. 만점 환산 없음") boolean excellentGrades,
    @Schema(description = "TOPIK 5급 이상") boolean excellentKorean,
    @Schema(description = "기본요건을 충족하고 우대조건이 하나 이상이면 5, 그 외 0", allowableValues = {"0", "5"}) int additionalWeekdayHours,
    @Schema(description = "TOPIK 미충족 시 주중·주말·공휴일·방학을 포함한 공통 주당 한도. 충족 시 null", nullable = true) Integer combinedWeeklyLimitHours,
    PeriodLimit termWeekdays,
    PeriodLimit weekendsAndHolidays,
    PeriodLimit vacations) {

    public enum LimitType { WEEKDAY_LIMIT, SHARED_WEEKLY_LIMIT, NO_TIME_CAP }
    public record PeriodLimit(
        @Schema(description = "WEEKDAY_LIMIT: 학기 중 주중 한도, SHARED_WEEKLY_LIMIT: combinedWeeklyLimitHours를 함께 사용하며 별도 가산 불가, NO_TIME_CAP: 서비스 기준 시간 상한 없음") LimitType type,
        @Schema(description = "WEEKDAY_LIMIT일 때만 시간 값. 공통 주당 한도와 상한 없음은 null", nullable = true) Integer hours) { }
}
