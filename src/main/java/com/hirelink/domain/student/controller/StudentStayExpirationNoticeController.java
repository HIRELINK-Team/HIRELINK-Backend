package com.hirelink.domain.student.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hirelink.domain.student.dto.StudentStayExpirationNoticeResponse;
import com.hirelink.domain.student.service.StudentStayExpirationNoticeService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name="Student Registration Renewal")
public class StudentStayExpirationNoticeController {
    private final StudentStayExpirationNoticeService service;
    public StudentStayExpirationNoticeController(StudentStayExpirationNoticeService service) { this.service=service; }

    @PostMapping("/api/students/me/stay-expiration-notice/claim")
    @Operation(summary="체류 만료 사전 안내 최초 표시 요청",description="온보딩 완료 후 화면에 안내를 표시할 준비가 되었을 때 호출한다. 인증된 최종 만료일이 한국 날짜 기준 0~30일 남았고 해당 만료일의 안내 이력이 없으면 show=true와 실제 남은 일수·문구를 반환하며 이력을 저장한다. 당일 문구는 오늘 체류기간이 만료돼요다. 동시 요청 중 하나만 true이며 날짜 변경·재접속·같은 만료일 갱신으로 재표시하지 않는다. 다른 새 만료일에는 다시 한 번 안내한다. show=true를 받으면 화면에 표시하고 기존 등록증 갱신 흐름으로 연결한다. 요청 성공 후 응답 유실 시에도 표시권은 이미 소모되어 재요청은 false다. 배지 회수·추천·지원 제한은 실행하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="최초 표시 여부",content=@Content(schema=@Schema(implementation=StudentStayExpirationNoticeResponse.class))),
        @ApiResponse(responseCode="401",description="로그인 필요",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
        @ApiResponse(responseCode="403",description="유학생 아님·탈퇴·온보딩 미완료",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
        @ApiResponse(responseCode="404",description="프로필 없음",content=@Content(schema=@Schema(implementation=ErrorResponse.class)))})
    public ResponseEntity<StudentStayExpirationNoticeResponse> claim(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.claim(Long.valueOf(jwt.getSubject())));
    }
}
