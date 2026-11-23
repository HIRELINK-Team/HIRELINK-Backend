package com.hirelink.domain.auth.controller;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.hirelink.domain.auth.dto.RegistrationRenewalFrontResponse;
import com.hirelink.domain.auth.dto.RegistrationRenewalVerificationResponse;
import com.hirelink.domain.auth.service.StudentRegistrationRenewalService;
import com.hirelink.domain.student.dto.StudentBadgeResponse;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/students/me/registration-renewals")
@Tag(name="Student Registration Renewal", description="가입 시 등록증 인증을 마친 유학생의 체류 만료일 갱신")
@ApiResponses({
    @ApiResponse(responseCode="400", description="파일 누락·UUID 형식 오류",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="401", description="로그인 필요",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="403", description="유학생이 아님 또는 탈퇴 계정",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="404", description="프로필 없음",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="409", description="REGISTRATION_REQUIRED, REGISTRATION_RENEWAL_NOT_FOUND/EXPIRED, REGISTRATION_FRONT/BACK_REQUIRED, STAY_DOCUMENT_NOT_REQUIRED, VALID_STAY_DOCUMENT_REQUIRED",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="413", description="FILE_TOO_LARGE: 5MB 초과",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="415", description="UNSUPPORTED_FILE_TYPE",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="422", description="OCR 인식 불가·지원하지 않는 체류자격·진위 확인 실패·PDF 번호 불일치/만료",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="502", description="OCR/진위 확인 외부 서비스 오류",content=@Content(schema=@Schema(implementation=ErrorResponse.class))),
    @ApiResponse(responseCode="503", description="등록증 확인 기능 또는 암호화 설정 미준비",content=@Content(schema=@Schema(implementation=ErrorResponse.class)))})
public class StudentRegistrationRenewalController {
    private final StudentRegistrationRenewalService service;
    public StudentRegistrationRenewalController(StudentRegistrationRenewalService service) { this.service=service; }

    @PostMapping(value="/front",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="갱신 등록증 앞면 추출",description="JPG/PNG 5MB 이하. 앞면에서 D-2 원문을 확인한다. D-2-2 등의 학업과정 세부 코드는 OCR하지 않는다. 최신 renewalId만 유효하며 30분 이내 완료해야 한다. 가입 당시 등록번호와의 동일인 대조는 제공하지 않는다. 기존 최종 만료일/배지는 유지한다.")
    @ApiResponse(responseCode="200",description="미확정 앞면 결과",content=@Content(schema=@Schema(implementation=RegistrationRenewalFrontResponse.class)))
    public ResponseEntity<RegistrationRenewalFrontResponse> front(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description="등록증 앞면 JPG/PNG",required=true) @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.front(Long.valueOf(jwt.getSubject()),file));
    }
    @PostMapping(value="/{renewalId}/back",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="갱신 등록증 뒷면 추출 및 진위 확인",description="JPG/PNG 5MB 이하. 만료일과 일련번호만 추출하고 진위 API를 호출한다. 허가일자·신고일은 추출/반환하지 않는다. 만료일이 지났으면 PDF가 필요하다. 아직 최종 반영하지 않는다. 뒷면 확인 후 재촬영하려면 앞면부터 다시 진행한다.")
    @ApiResponse(responseCode="200",description="미확정 뒷면 결과",content=@Content(schema=@Schema(implementation=RegistrationRenewalVerificationResponse.class)))
    public ResponseEntity<RegistrationRenewalVerificationResponse> back(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID renewalId,
            @Parameter(description="등록증 뒷면 JPG/PNG",required=true) @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.back(Long.valueOf(jwt.getSubject()),renewalId,file));
    }
    @PostMapping(value="/{renewalId}/stay-document",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="최신 체류기간 증빙 PDF 추출",description="카드 만료일이 지난 경우 PDF 5MB 이하를 제출한다. 이번 갱신 등록증과 PDF의 등록번호 일치 및 최신 만료일을 확인한다. 진위 API를 다시 호출하지 않는다. 허가일자·신고일은 추출/반환하지 않는다. 성공해도 최종 확인 전 기존 만료일/배지는 유지한다.")
    @ApiResponse(responseCode="200",description="미확정 PDF 결과",content=@Content(schema=@Schema(implementation=RegistrationRenewalVerificationResponse.class)))
    public ResponseEntity<RegistrationRenewalVerificationResponse> document(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID renewalId,
            @Parameter(description="최신 체류기간 증빙 PDF",required=true) @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.stayDocument(Long.valueOf(jwt.getSubject()),renewalId,file));
    }
    @PostMapping("/{renewalId}/confirm")
    @Operation(summary="갱신 결과 최종 확인",description="renewalId만 받으며 수정한 추출값을 받지 않는다. 한국 날짜 기준 만료일 당일까지 유효하다. 성공할 때 인증 기록/프로필 만료일을 함께 갱신하고 이미 최종 등록된 세 서류가 있으면 배지를 발급/재발급한다. 세 서류 재제출은 필요 없다. 실패하면 기존 최종 값과 배지를 유지한다. 추천/지원 제한 해제는 별도 담당 연결 범위이다.")
    @ApiResponse(responseCode="200",description="최종 만료일과 현재 배지 상태",content=@Content(schema=@Schema(implementation=StudentBadgeResponse.class)))
    public ResponseEntity<StudentBadgeResponse> confirm(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID renewalId) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.confirm(Long.valueOf(jwt.getSubject()),renewalId));
    }
}
