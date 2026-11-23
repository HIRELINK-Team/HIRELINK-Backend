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
    @Operation(summary="유학생 탈퇴 신청",description="confirmed=true로 신청한다. 즉시 휴면·기존 로그인 토큰 폐기. 30일 경계 전 새 소셜 로그인 결과 교환으로 자동 복구하고 서류를 유지한다. 30일 이후 복구 불가. 백그라운드에서 원본 실제 삭제와 Google/Kakao 연결 해제를 성공할 때까지 재시도한다. 사업주 열람·친구 매칭 로직은 이 API에서 변경하지 않는다.")
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
