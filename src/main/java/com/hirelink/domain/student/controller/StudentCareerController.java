package com.hirelink.domain.student.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.service.StudentCareerService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/students/me/careers")
@Tag(name = "Student Careers", description = "개별 쓰기 종료. 프로필 PATCH 최종 일괄 저장 사용")
@ApiResponses({
	@ApiResponse(responseCode = "400", description = "INVALID_INPUT: 일괄 저장 API 사용 필요", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
		examples = @ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"경력은 내 정보의 수정 반영하기에서 전체 목록으로 저장해주세요.\"}"))),
	@ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
		examples = @ExampleObject(name = "UNAUTHORIZED", value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}"))),
	@ApiResponse(responseCode = "403", description = "유학생 아님 또는 탈퇴 계정", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
		examples = @ExampleObject(name = "FORBIDDEN", value = "{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"))),
	@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
		examples = @ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")))
})
public class StudentCareerController {

	private final StudentCareerService service;

	public StudentCareerController(StudentCareerService service) {
		this.service = service;
	}

	@Operation(summary = "경력 개별 추가 종료", deprecated = true, description = "DB를 변경하지 않는다. 경력 임시 변경은 프로필 PATCH 최종 일괄 저장에 전체 목록으로 전달한다.")
	@PostMapping
	public void create(@AuthenticationPrincipal Jwt jwt) {
		service.rejectStandaloneWrite(Long.valueOf(jwt.getSubject()));
	}

	@Operation(summary = "경력 개별 수정 종료", deprecated = true, description = "DB를 변경하지 않는다. 경력 임시 변경은 프로필 PATCH 최종 일괄 저장에 전체 목록으로 전달한다.")
	@PatchMapping("/{careerId}")
	public void update(@AuthenticationPrincipal Jwt jwt) {
		service.rejectStandaloneWrite(Long.valueOf(jwt.getSubject()));
	}

	@Operation(summary = "경력 개별 삭제 종료", deprecated = true, description = "DB를 변경하지 않는다. 경력 임시 변경은 프로필 PATCH 최종 일괄 저장에 전체 목록으로 전달한다.")
	@DeleteMapping("/{careerId}")
	public void delete(@AuthenticationPrincipal Jwt jwt) {
		service.rejectStandaloneWrite(Long.valueOf(jwt.getSubject()));
	}
}
