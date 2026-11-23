package com.hirelink.domain.student.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.service.EmployerStudentDocumentService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Employer Student Documents", description = "매칭된 유학생의 최종 등록 증명서 원본 열람")
public class EmployerStudentDocumentController {
	private final EmployerStudentDocumentService service;

	public EmployerStudentDocumentController(EmployerStudentDocumentService service) {
		this.service = service;
	}

	@GetMapping("/api/employers/me/matches/{matchId}/student-documents/{kind}")
	@Operation(summary = "매칭 유학생 증명서 원본 열람", description = "해당 매칭의 사업주만 최신 확정 원본을 조회한다. 삭제된 매칭으로는 조회할 수 없으며 다른 유효한 매칭의 ID로 조회해야 한다. 지원·제안 대기 단계, 외국인등록증·미확정 파일은 제공하지 않는다. 탈퇴 신청 후 정확히 30일 전까지는 허용하고 그 시각부터는 최종 정리 지연 여부와 관계없이 차단한다. 저장소 URL 대신 PDF/JPEG/PNG 파일을 반환하며 각 요청에 Bearer 인증이 필요하다. 원본 파일이나 채용 상태는 변경하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "최신 확정 원본 파일", content = {
			@Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
			@Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
			@Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))}),
		@ApiResponse(responseCode = "400", description = "INVALID_INPUT 또는 INVALID_DOCUMENT_TYPE", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "사업주가 아님·다른 사업주의 매칭·탈퇴 복구 기한 경과·최종 탈퇴", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "EMPLOYMENT_MATCH_NOT_FOUND: 없거나 삭제된 매칭. DOCUMENT_NOT_REGISTERED: 최종 등록 서류 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "502", description = "STUDENT_DOCUMENT_STORAGE_FAILED: 저장소 조회 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
	public ResponseEntity<byte[]> read(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long matchId,
			@PathVariable @Parameter(schema = @Schema(allowableValues = {"enrollment", "transcript", "topik"})) String kind,
			HttpServletResponse response) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		var content = service.read(Long.valueOf(jwt.getSubject()), matchId, kind);
		String extension = content.contentType().equals(MediaType.APPLICATION_PDF) ? "pdf"
				: content.contentType().equals(MediaType.IMAGE_JPEG) ? "jpg" : "png";
		return ResponseEntity.ok().contentType(content.contentType()).contentLength(content.bytes().length)
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(kind + "." + extension).build().toString())
				.header("X-Content-Type-Options", "nosniff").body(content.bytes());
	}
}
