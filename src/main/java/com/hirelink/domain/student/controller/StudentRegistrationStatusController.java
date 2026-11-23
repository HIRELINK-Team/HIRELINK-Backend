package com.hirelink.domain.student.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hirelink.domain.auth.dto.RegistrationRenewalConfirmationResponse;
import com.hirelink.domain.student.service.StudentRegistrationStatusService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name="Student Registration Renewal")
public class StudentRegistrationStatusController {
    private final StudentRegistrationStatusService service;
    public StudentRegistrationStatusController(StudentRegistrationStatusService service) { this.service=service; }

    @GetMapping("/api/students/me/registration")
    @Operation(summary="현재 등록증 정보와 서비스 지원 대상 조회",description="최종 확인된 이름·국적·원문 체류자격·세부 코드·만료일을 조회한다. 진행 중인 갱신 값은 반영하지 않는다. D-2만 저장된 학업과정으로 조합하며 D-10 등은 원문을 유지한다. 서비스 지원 여부는 배지 회수·추천·지원 제한을 실행하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="최종 등록증 정보",content=@Content(schema=@Schema(implementation=RegistrationRenewalConfirmationResponse.class))),
        @ApiResponse(responseCode="401",description="로그인 필요",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
        @ApiResponse(responseCode="403",description="유학생 아님·탈퇴·온보딩 미완료",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
        @ApiResponse(responseCode="404",description="프로필 없음",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
        @ApiResponse(responseCode="409",description="REGISTRATION_REQUIRED",content=@Content(schema=@Schema(implementation=ErrorResponse.class)))})
    public ResponseEntity<RegistrationRenewalConfirmationResponse> current(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.current(Long.valueOf(jwt.getSubject())));
    }
}
