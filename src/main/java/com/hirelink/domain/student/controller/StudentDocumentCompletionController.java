package com.hirelink.domain.student.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hirelink.domain.student.dto.StudentDocumentCompletionResponse;
import com.hirelink.domain.student.service.StudentDocumentCompletionService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Student Document Completion", description = "마이페이지 서류 등록 완성률 조회")
public class StudentDocumentCompletionController {
    private final StudentDocumentCompletionService service;
    public StudentDocumentCompletionController(StudentDocumentCompletionService service) { this.service = service; }

    @GetMapping("/api/students/me/documents/completion")
    @Operation(summary = "본인의 서류 등록 완성률 조회", description = "등록증 인증 완료 및 유효한 최종 체류 만료일, 재학·성적·TOPIK 최종 등록을 각각 25% 반영한다. 합계는 0·25·50·75·100이다. 한국 날짜로 만료일 당일까지 등록증 25%를 유지하고 다음 날 제외한다. 갱신 최종 성공 시에만 유효한 만료일을 반영한다. 갱신 실패·중단·OCR 추출만 완료된 상태는 기존 확정 정보를 바꾸지 않는다. 조회는 DB 정보를 변경하거나 배지를 회수하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "완성률과 네 항목 반영 여부", content = @Content(schema = @Schema(implementation = StudentDocumentCompletionResponse.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "유학생이 아님 또는 탈퇴 계정", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "프로필 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<StudentDocumentCompletionResponse> current(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(service.current(Long.valueOf(jwt.getSubject())));
    }
}
