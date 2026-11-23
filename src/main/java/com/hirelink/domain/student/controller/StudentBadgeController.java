package com.hirelink.domain.student.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hirelink.domain.student.dto.StudentBadgeResponse;
import com.hirelink.domain.student.service.StudentBadgeService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name="Student Badge", description="현재 HIRELINK 인증 배지 조회")
public class StudentBadgeController {
    private final StudentBadgeService service;
    public StudentBadgeController(StudentBadgeService service) { this.service=service; }
    @GetMapping("/api/students/me/badge")
    @Operation(summary="본인의 인증 배지 상태 조회", description="최종 등록된 등록증·재학·성적·TOPIK 및 한국 날짜의 체류 만료일로 현재 유효 여부를 판단한다. 조회는 발급/회수 처리를 하지 않는다. issuedAt만으로 유효 여부를 판단하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="현재 상태",content=@Content(schema=@Schema(implementation=StudentBadgeResponse.class))),
        @ApiResponse(responseCode="401",description="로그인 필요",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
        @ApiResponse(responseCode="403",description="유학생이 아님 또는 탈퇴 계정",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
        @ApiResponse(responseCode="404",description="프로필 없음",content=@Content(schema=@Schema(implementation=ErrorResponse.class)))})
    public ResponseEntity<StudentBadgeResponse> current(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.current(Long.valueOf(jwt.getSubject())));
    }
}
