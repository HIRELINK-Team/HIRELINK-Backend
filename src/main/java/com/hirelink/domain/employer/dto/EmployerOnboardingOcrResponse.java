package com.hirelink.domain.employer.dto;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "등록증에서 인식한 사업자등록번호. 사용자 수정 가능. 주소는 별도 검색으로 입력한다.")
public record EmployerOnboardingOcrResponse(
        @Schema(example = "123-45-67890", requiredMode = Schema.RequiredMode.REQUIRED) String businessRegistrationNumber) {}
