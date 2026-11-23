package com.hirelink.domain.student.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hirelink.domain.student.dto.StudentWorkingConditionsResponse;
import com.hirelink.domain.student.service.StudentWorkingConditionsService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Student Working Conditions", description = "현재 배지를 가진 유학생의 예상 근무시간 기준")
public class StudentWorkingConditionsController {
    private final StudentWorkingConditionsService service;
    public StudentWorkingConditionsController(StudentWorkingConditionsService service) { this.service = service; }
    @GetMapping("/api/students/me/working-conditions")
    @Operation(summary = "나의 근무 가능 조건 조회", description = "현재 유효한 배지가 있어야 조회한다. 배지 발급 시 프로필에 저장한 근무 가능 조건을 제공하며, 조회 시 최종 등록 서류와 적용일이 도래한 최신 인증대학 기준으로 계산·저장한다. 미확정 OCR 값은 사용하지 않는다. TOPIK 기본요건 미충족자의 한도는 주말·공휴일·방학을 포함한 공통 주당 한도이며 별도로 더하지 않는다. 충족자는 학기 중 주중 25/30시간에 우대 최대5시간을 더하고 주말·공휴일·방학은 시간 상한 없음으로 제공한다. 학적 상태·KIIP·학교별 학사일정은 사용하지 않으며 실제 허가 완료를 뜻하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "예상 근무시간 기준", content = @Content(schema = @Schema(implementation = StudentWorkingConditionsResponse.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "유학생이 아님, 탈퇴 계정 또는 ACTIVE_BADGE_REQUIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "프로필 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "WORKING_CONDITIONS_DATA_INCOMPLETE: 과정/세부 비자 불일치 또는 최종 서류값 누락/지원 범위 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "ACCREDITED_UNIVERSITY_LIST_UNAVAILABLE: 적용 목록 없음/불완전", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<StudentWorkingConditionsResponse> current(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(service.current(Long.valueOf(jwt.getSubject())));
    }
}
