package com.hirelink.domain.student.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.dto.StudentCareerCreateRequest;
import com.hirelink.domain.student.dto.StudentCareerResponse;
import com.hirelink.domain.student.dto.StudentCareerUpdateRequest;
import com.hirelink.domain.student.service.StudentCareerService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Student Careers", description = "유학생 본인 경력 관리")
@RestController
@RequestMapping("/api/students/me/careers")
public class StudentCareerController {

	private final StudentCareerService studentCareerService;

	public StudentCareerController(StudentCareerService studentCareerService) {
		this.studentCareerService = studentCareerService;
	}

	@Operation(summary = "경력 추가",
			description = "프로필이 등록된 유학생 본인의 경력을 추가한다. 경력명 · 시작월 · 종료월 · 근무 내용 모두 필수다. 근무 내용 생략 · null · 빈 문자열 · 공백만 입력은 400 INVALID_INPUT이다. 시작월과 종료월은 YYYY-MM이며 한국 기준 이번 달까지 허용한다. 종료월은 시작월 이상이어야 한다. 성공 시 즉시 DB에 저장하며 온보딩 전체 일괄 저장 API가 아니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentCareerResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"careerId\":1,\"careerName\":\"카페 아르바이트\",\"startMonth\":\"2026-01\",\"endMonth\":\"2026-06\",\"description\":\"음료 제조 및 고객 응대\"}"))),
		@ApiResponse(responseCode = "400", description = "입력 형식 또는 값 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"요청 형식이 올바르지 않습니다.\"}"),
							@ExampleObject(name = "REQUIRED_DESCRIPTION", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"근무 내용을 입력해주세요.\"}")
						})),
		@ApiResponse(responseCode = "401", description = "유효한 인증 정보 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "UNAUTHORIZED", value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}")
						})),
		@ApiResponse(responseCode = "403", description = "접근 권한 없음 / 탈퇴한 계정",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "FORBIDDEN", value = "{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"),
							@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
						})),
		@ApiResponse(responseCode = "404", description = "유학생 프로필 미등록",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "STUDENT_PROFILE_NOT_FOUND", value = "{\"code\":\"STUDENT_PROFILE_NOT_FOUND\",\"message\":\"유학생 프로필이 아직 등록되지 않았습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<StudentCareerResponse> createCareer(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody StudentCareerCreateRequest request) {
		StudentCareerResponse response = studentCareerService.createCareer(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "경력 수정",
			description = "보내지 않은 필드는 유지한다. 빈 요청과 수정 대상이 아닌 필드는 400이다. 본인 경력만 수정한다. description의 null · 빈 문자열 · 공백만 입력은 400 INVALID_INPUT이며 내용을 삭제할 수 없다. 생략하면 기존 내용을 유지하되 기존 내용이 비어 있으면 description을 함께 보내야 한다. 나머지 필드도 null 불가다. 수정 후 시작월 · 종료월은 한국 기준 이번 달까지, 종료월은 시작월 이상이어야 한다. 성공 시 즉시 DB에 저장하며 검증 실패 시 기존 경력을 변경하지 않는다. 온보딩 전체 일괄 저장 API가 아니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentCareerResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"careerId\":1,\"careerName\":\"카페 아르바이트\",\"startMonth\":\"2026-01\",\"endMonth\":\"2026-06\",\"description\":\"음료 제조 및 고객 응대\"}"))),
		@ApiResponse(responseCode = "400", description = "입력 형식 또는 값 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"요청 형식이 올바르지 않습니다.\"}"),
							@ExampleObject(name = "REQUIRED_DESCRIPTION", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"근무 내용을 입력해주세요.\"}")
						})),
		@ApiResponse(responseCode = "401", description = "유효한 인증 정보 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "UNAUTHORIZED", value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}")
						})),
		@ApiResponse(responseCode = "403", description = "접근 권한 없음 / 탈퇴한 계정",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "FORBIDDEN", value = "{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"),
							@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
						})),
		@ApiResponse(responseCode = "404", description = "경력 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "STUDENT_CAREER_NOT_FOUND", value = "{\"code\":\"STUDENT_CAREER_NOT_FOUND\",\"message\":\"경력 정보를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PatchMapping(value = "/{careerId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<StudentCareerResponse> updateCareer(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "경력 ID (Long 정수)", example = "1") @PathVariable Long careerId, @Valid @RequestBody StudentCareerUpdateRequest request) {
		StudentCareerResponse response = studentCareerService.updateCareer(Long.valueOf(jwt.getSubject()), careerId, request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "경력 삭제", description = "유학생이 본인 경력을 DB에서 삭제한다. 성공 시 204로 응답 본문이 없다. 타인 경력은 403, 없거나 이미 삭제한 경력은 404다.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "삭제 완료. 응답 본문 없음", content = @Content),
		@ApiResponse(responseCode = "400", description = "입력 형식 또는 값 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"요청 형식이 올바르지 않습니다.\"}")
						})),
		@ApiResponse(responseCode = "401", description = "유효한 인증 정보 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "UNAUTHORIZED", value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}")
						})),
		@ApiResponse(responseCode = "403", description = "접근 권한 없음 / 탈퇴한 계정",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "FORBIDDEN", value = "{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"),
							@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")
						})),
		@ApiResponse(responseCode = "404", description = "경력 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "STUDENT_CAREER_NOT_FOUND", value = "{\"code\":\"STUDENT_CAREER_NOT_FOUND\",\"message\":\"경력 정보를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@DeleteMapping("/{careerId}")
	public ResponseEntity<Void> deleteCareer(@AuthenticationPrincipal Jwt jwt, @Parameter(description = "경력 ID (Long 정수)", example = "1") @PathVariable Long careerId) {
		studentCareerService.deleteCareer(Long.valueOf(jwt.getSubject()), careerId);
		return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
	}
}
