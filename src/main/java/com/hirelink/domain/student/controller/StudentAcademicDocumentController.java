package com.hirelink.domain.student.controller;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.hirelink.domain.student.dto.AcademicDocumentResponse;
import com.hirelink.domain.student.service.StudentAcademicDocumentService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/students/me/documents/{kind:transcript|topik}")
@Tag(name="Student Academic Documents",description="성적·TOPIK OCR 최종 등록. 직접 수정·개별 삭제 없음. 사업주 원본 열람은 Employer Student Documents API를 사용한다.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="파일 누락·잘못된 요청",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
 @ApiResponse(responseCode="401",description="로그인 필요",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
 @ApiResponse(responseCode="403",description="유학생 아님 또는 탈퇴 상태",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
 @ApiResponse(responseCode="404",description="프로필·최종 등록 서류 없음",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
 @ApiResponse(responseCode="409",description="DOCUMENT_DRAFT_NOT_FOUND",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
 @ApiResponse(responseCode="413",description="FILE_TOO_LARGE",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
 @ApiResponse(responseCode="415",description="UNSUPPORTED_FILE_TYPE",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
 @ApiResponse(responseCode="422",description="UNREADABLE_STUDENT_DOCUMENT. 직전 학기 평균 학점 또는 TOPIK 등급 미인식을 message로 안내한다. 파일·서류 유형 자체를 읽지 못하면 일반 재업로드 안내를 반환한다",content=@Content(mediaType = "application/json", schema=@Schema(implementation=ErrorResponse.class),examples={
  @ExampleObject(name="TRANSCRIPT_GPA_UNREADABLE",value="{\"code\":\"UNREADABLE_STUDENT_DOCUMENT\",\"message\":\"직전 학기 평균 학점을 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.\"}"),
  @ExampleObject(name="TOPIK_LEVEL_UNREADABLE",value="{\"code\":\"UNREADABLE_STUDENT_DOCUMENT\",\"message\":\"TOPIK 등급을 확인하지 못했습니다. 해당 정보가 선명하게 보이도록 다시 촬영하거나 파일을 올려주세요.\"}"),
  @ExampleObject(name="UNREADABLE_STUDENT_DOCUMENT",value="{\"code\":\"UNREADABLE_STUDENT_DOCUMENT\",\"message\":\"서류 정보를 확인하지 못했습니다. 다시 촬영하거나 업로드해주세요.\"}")})),
 @ApiResponse(responseCode="502",description="OCR·저장소 실패",content=@Content(schema=@Schema(implementation=ErrorResponse.class)))})
public class StudentAcademicDocumentController {
    private static final String TRANSCRIPT_PREVIEW = """
        {"documentType":"TRANSCRIPT","draftId":"ad0856ec-3b1e-4d1d-88cd-1f5063aa2720","extracted":{"academicYear":2025,"semester":"SECOND","averageGpa":4.24,"topikLevel":null},"registeredAt":null}
        """;
    private static final String TOPIK_PREVIEW = """
        {"documentType":"TOPIK","draftId":"ad0856ec-3b1e-4d1d-88cd-1f5063aa2720","extracted":{"academicYear":null,"semester":null,"averageGpa":null,"topikLevel":4},"registeredAt":null}
        """;
    private static final String TRANSCRIPT_CONFIRMED = """
        {"documentType":"TRANSCRIPT","draftId":null,"extracted":{"academicYear":2025,"semester":"SECOND","averageGpa":4.24,"topikLevel":null},"registeredAt":"2026-11-29T03:00:00Z"}
        """;
    private static final String TOPIK_CONFIRMED = """
        {"documentType":"TOPIK","draftId":null,"extracted":{"academicYear":null,"semester":null,"averageGpa":null,"topikLevel":4},"registeredAt":"2026-11-29T03:00:00Z"}
        """;

    private final StudentAcademicDocumentService service;
    public StudentAcademicDocumentController(StudentAcademicDocumentService service) { this.service=service; }
    @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="성적·TOPIK OCR 미리보기",description="5MB 이하 PDF/JPEG/PNG. transcript는 가장 최근 학기 평균 학점이며 누적·과목별 점수와 만점 기준은 제외한다. topik은 1~6급이다. 최종 자료는 변경하지 않는다. OCR은 진위 확인이 아니다.")
    @ApiResponse(responseCode="200", description="OCR 미리보기 성공. draftId를 최종 등록 요청에 사용하며 registeredAt은 null이다.", content=@Content(
        mediaType=MediaType.APPLICATION_JSON_VALUE, schema=@Schema(implementation=AcademicDocumentResponse.class), examples={
            @ExampleObject(name="TRANSCRIPT", summary="성적증명서", value=TRANSCRIPT_PREVIEW),
            @ExampleObject(name="TOPIK", summary="TOPIK 성적증명서", value=TOPIK_PREVIEW)}))
    public ResponseEntity<AcademicDocumentResponse> preview(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Parameter(description="서류 종류: transcript=성적증명서, topik=TOPIK 성적증명서. 소문자만 허용하며 재학증명서(enrollment)는 별도 API이다.", 
                schema=@Schema(type="string", allowableValues={"transcript", "topik"})) String kind,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.preview(Long.valueOf(jwt.getSubject()),kind,file));
    }
    @PostMapping("/{draftId}/confirm")
    @Operation(summary="성적·TOPIK 최종 등록",description="해당 종류의 본인 최신 draftId만 확정하며 추출값 입력은 받지 않는다. 성공 후 해당 프로필 값·원본만 갱신하고 이전 원본을 삭제한다. 실패·중단하면 기존 최종 자료를 유지한다.")
    @ApiResponse(responseCode="200", description="최종 등록 성공. draftId는 null이고 registeredAt은 최종 등록 시각이다.", content=@Content(
        mediaType=MediaType.APPLICATION_JSON_VALUE, schema=@Schema(implementation=AcademicDocumentResponse.class), examples={
            @ExampleObject(name="TRANSCRIPT", summary="성적증명서", value=TRANSCRIPT_CONFIRMED),
            @ExampleObject(name="TOPIK", summary="TOPIK 성적증명서", value=TOPIK_CONFIRMED)}))
    public ResponseEntity<AcademicDocumentResponse> confirm(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Parameter(description="서류 종류: transcript=성적증명서, topik=TOPIK 성적증명서. 소문자만 허용하며 재학증명서(enrollment)는 별도 API이다.", 
                schema=@Schema(type="string", allowableValues={"transcript", "topik"})) String kind,
            @PathVariable UUID draftId) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.confirm(Long.valueOf(jwt.getSubject()),kind,draftId));
    }
    @GetMapping
    @Operation(summary="본인의 최종 성적·TOPIK 정보",description="최종 추출값과 등록 시각을 반환하며 미확정 값·원본 키·만료일은 반환하지 않는다.")
    @ApiResponse(responseCode="200", description="최종 등록 정보 조회 성공. draftId는 null이고 registeredAt은 최종 등록 시각이다.", content=@Content(
        mediaType=MediaType.APPLICATION_JSON_VALUE, schema=@Schema(implementation=AcademicDocumentResponse.class), examples={
            @ExampleObject(name="TRANSCRIPT", summary="성적증명서", value=TRANSCRIPT_CONFIRMED),
            @ExampleObject(name="TOPIK", summary="TOPIK 성적증명서", value=TOPIK_CONFIRMED)}))
    public ResponseEntity<AcademicDocumentResponse> current(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Parameter(description="서류 종류: transcript=성적증명서, topik=TOPIK 성적증명서. 소문자만 허용하며 재학증명서(enrollment)는 별도 API이다.", 
                schema=@Schema(type="string", allowableValues={"transcript", "topik"})) String kind) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.current(Long.valueOf(jwt.getSubject()),kind));
    }
}
