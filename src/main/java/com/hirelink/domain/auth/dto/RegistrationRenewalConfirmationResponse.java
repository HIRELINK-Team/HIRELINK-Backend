package com.hirelink.domain.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import com.hirelink.domain.student.entity.StudyProgramType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="최종 확인된 등록증 정보와 현재 서비스 지원 여부. 지원 여부는 진위 확인 결과와 구분한다")
public record RegistrationRenewalConfirmationResponse(
        String name,
        @Schema(description="ISO 국가 코드") String nationalityCode,
        @Schema(description="등록증에서 확인한 원문 체류자격. 예: D-2, D-10") String registrationVisaCode,
        @Schema(description="D-2이면 저장된 학업과정과 조합한 코드, 그 외에는 등록증 원문 코드") String visaCode,
        @Schema(description="기존에 저장된 학업과정. 갱신으로 변경하지 않으며 기존 미입력은 null") StudyProgramType studyProgram,
        LocalDate stayExpirationDate,
        @Schema(description="등록증 D-2와 지원 학업과정 조합 여부. 취업 허가·추천·지원 가능 여부를 의미하지 않는다") boolean serviceSupported,
        @Schema(description="지원 대상이 아니면 안내 문구, 지원 대상이면 null") String supportMessage,
        @Schema(description="기존 배지 조회 기준으로 판단한 현재 상태") boolean active,
        Instant issuedAt) { }
