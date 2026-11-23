package com.hirelink.domain.student.controller;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.hirelink.domain.student.dto.EnrollmentDocumentResponse;
import com.hirelink.domain.student.dto.EnrollmentPreviewResponse;
import com.hirelink.domain.student.service.StudentEnrollmentService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/students/me/documents/enrollment")
@Tag(name = "Student Enrollment", description = "재학증명서 OCR 확인 및 최종 등록")
@ApiResponses({
	@ApiResponse(responseCode = "401", description = "유효한 계정 인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
	@ApiResponse(responseCode = "403", description = "유학생이 아님 또는 탈퇴 계정", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
	@ApiResponse(responseCode = "404", description = "프로필 또는 최종 등록 서류 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class StudentEnrollmentController {
	private final StudentEnrollmentService service;
	public StudentEnrollmentController(StudentEnrollmentService service) { this.service = service; }

	@PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "재학증명서 업로드 및 추출 결과 확인", description = "PDF/JPG/PNG, 5MB 이하. 명시된 학년을 우선하고 없으면 개인의 현재·이수 학기 수/2를 올림한다. 다음 학기를 더하지 않고 학사 일정은 제외한다. 1~4학년만 허용한다. 성공해도 최종 등록 자료·프로필은 변경하지 않는다. 재업로드 성공 시 이전 미확정 결과를 교체한다. 인식 실패·범위 초과 시 기존 최종 원본과 추출값을 유지한다. OCR은 서류 진위 확인이 아니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "미확정 추출 결과", content = @Content(schema = @Schema(implementation = EnrollmentPreviewResponse.class))),
		@ApiResponse(responseCode = "400", description = "파일 누락 또는 multipart 형식 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "415", description = "UNSUPPORTED_FILE_TYPE", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "422", description = "인식 불가 또는 학년 범위 초과", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "UNSUPPORTED_GRADE_LEVEL", value = "{\"code\":\"UNSUPPORTED_GRADE_LEVEL\",\"message\":\"지원하는 학년 범위를 벗어났습니다\"}"),
			@ExampleObject(name = "UNREADABLE_ENROLLMENT", value = "{\"code\":\"UNREADABLE_ENROLLMENT\",\"message\":\"재학증명서 정보를 확인하지 못했습니다. 다시 촬영하거나 업로드해주세요.\"}"),
			@ExampleObject(name = "UNIVERSITY_UNREADABLE", value = "{\"code\":\"UNREADABLE_ENROLLMENT\",\"message\":\"대학교명을 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.\"}"),
			@ExampleObject(name = "GRADE_UNREADABLE", value = "{\"code\":\"UNREADABLE_ENROLLMENT\",\"message\":\"학년 또는 학기 수를 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.\"}"),
			@ExampleObject(name = "UNIVERSITY_AND_GRADE_UNREADABLE", value = "{\"code\":\"UNREADABLE_ENROLLMENT\",\"message\":\"대학교명과 학년 또는 학기 수를 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.\"}")})),
		@ApiResponse(responseCode = "502", description = "ENROLLMENT_OCR_FAILED 또는 STUDENT_DOCUMENT_STORAGE_FAILED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EnrollmentPreviewResponse> preview(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "재학증명서 원본", required = true) @RequestPart("file") MultipartFile file) {
		return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.upload(Long.valueOf(jwt.getSubject()), file));
	}

	@PostMapping("/{draftId}/confirm")
	@Operation(summary = "확인한 재학증명서 최종 등록", description = "본인의 최신 draftId만 전달한다. 사용자 수정 추출값은 받지 않으며 서버 보관 결과를 다시 범위 검사한다. 원본 연결·프로필 학교/학년을 같은 트랜잭션으로 갱신하고 성공 후 이전 원본을 삭제한다. 실패하면 기존 최종 자료를 유지한다. 학교의 캠퍼스는 서류 정보에 함께 저장한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "최종 등록 완료", content = @Content(schema = @Schema(implementation = EnrollmentDocumentResponse.class))),
		@ApiResponse(responseCode = "400", description = "draftId 형식 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "409", description = "ENROLLMENT_DRAFT_NOT_FOUND: 다른 회원·이전·이미 확인한 결과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "422", description = "UNSUPPORTED_GRADE_LEVEL 또는 UNREADABLE_ENROLLMENT. 누락된 대학교·학년/학기 수를 message로 안내하며 여러 항목이면 함께 안내한다", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "UNIVERSITY_UNREADABLE", value = "{\"code\":\"UNREADABLE_ENROLLMENT\",\"message\":\"대학교명을 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.\"}"),
			@ExampleObject(name = "GRADE_UNREADABLE", value = "{\"code\":\"UNREADABLE_ENROLLMENT\",\"message\":\"학년 또는 학기 수를 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.\"}"),
			@ExampleObject(name = "UNIVERSITY_AND_GRADE_UNREADABLE", value = "{\"code\":\"UNREADABLE_ENROLLMENT\",\"message\":\"대학교명과 학년 또는 학기 수를 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.\"}")}))
	})
	public ResponseEntity<EnrollmentDocumentResponse> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID draftId) {
		return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.confirm(Long.valueOf(jwt.getSubject()), draftId));
	}

	@GetMapping
	@Operation(summary = "본인의 최종 재학증명서 정보 조회", description = "미확정 추출값·원본 경로는 제공하지 않는다. 최종 등록된 대학교·캠퍼스·학년·등록 시각을 반환한다.")
	@ApiResponse(responseCode = "200", description = "최종 등록 정보", content = @Content(schema = @Schema(implementation = EnrollmentDocumentResponse.class)))
	public ResponseEntity<EnrollmentDocumentResponse> current(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.current(Long.valueOf(jwt.getSubject())));
	}
}
