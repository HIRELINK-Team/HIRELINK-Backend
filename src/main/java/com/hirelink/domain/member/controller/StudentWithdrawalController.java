package com.hirelink.domain.member.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import com.hirelink.domain.member.service.StudentWithdrawalService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/students/me/withdrawal")
@Tag(name="Student Withdrawal")
public class StudentWithdrawalController {
    private final StudentWithdrawalService service;
    private final boolean secure;
    public StudentWithdrawalController(StudentWithdrawalService service,@Value("${auth.cookie.secure:true}") boolean secure) { this.service=service; this.secure=secure; }
    public record Request(@NotNull @AssertTrue Boolean confirmed) { }
    @PostMapping
    @Operation(summary="유학생 탈퇴 신청",description="confirmed=true로 신청한다. 즉시 휴면·전체 로그인 토큰 폐기. 신청 후 정확히 30일 전까지 새 소셜 로그인으로 복구한다. 30일 이후 복구 불가. 백그라운드에서 증명서 원본 삭제와 Google/Kakao 연결 해제를 재시도하며, 성공 시 최종 탈퇴한다. 이름·이메일·전화번호와 확정 서류 등록 시각·추출값 등 보존 대상은 최종 탈퇴일부터 3년 보관 후 정리한다. 보존 서류는 일반 조회에서 제외하며 원본 위치는 남기지 않는다. 재가입은 새 계정으로 전체 가입 절차를 진행한다. 지원·제안·매칭·추천·알림 기록의 개인정보 정리는 포함하지 않는다.")
    @ApiResponses({
      @ApiResponse(responseCode="200",description="휴면 시작, 복구 기한 반환"),
      @ApiResponse(responseCode="400",description="탈퇴 확인 누락",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
      @ApiResponse(responseCode="401",description="인증 필요",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
      @ApiResponse(responseCode="403",description="유학생 아님·이미 탈퇴",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
      @ApiResponse(responseCode="409",description="SOCIAL_REAUTH_REQUIRED: 연결 해제 정보 확보를 위한 소셜 재인증 필요",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
      @ApiResponse(responseCode="503",description="WITHDRAWAL_NOT_CONFIGURED",content=@Content(schema=@Schema(implementation=ErrorResponse.class)))})
    public ResponseEntity<StudentWithdrawalService.Result> request(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Request request) {
        var result=service.request(Long.valueOf(jwt.getSubject()));
        return ResponseEntity.ok().header("Cache-Control","no-store").header("Set-Cookie",ResponseCookie.from("refreshToken","").path("/api/auth").httpOnly(true).secure(secure).sameSite("Lax").maxAge(0).build().toString()).body(result);
    }
}
