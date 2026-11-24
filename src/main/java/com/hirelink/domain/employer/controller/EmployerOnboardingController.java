package com.hirelink.domain.employer.controller;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import com.hirelink.domain.employer.dto.*;
import com.hirelink.domain.employer.service.EmployerOnboardingService;
import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyRequest;
import com.hirelink.domain.workplace.dto.BusinessRegistrationVerifyResponse;
import com.hirelink.global.exception.ErrorResponse;
import com.hirelink.global.exception.InvalidWorkplaceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/employers/me/onboarding")
@Tag(name = "Employer Onboarding", description = "전화번호 인증으로 계정 생성 후 진행하는 사업주 온보딩. 모든 요청은 로그인 Bearer 토큰을 사용한다.")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "잘못된 입력 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "유효한 로그인 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "사업주가 아닌 계정 또는 탈퇴 계정", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class EmployerOnboardingController {
    private final EmployerOnboardingService service;
    public EmployerOnboardingController(EmployerOnboardingService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "사업주 온보딩 상태 조회", description = "REQUIRED이면 항상 이름·유입 경로부터 다시 입력한다. 가입 화면 진입 시 POST /restart로 새 시도를 만들고 화면 초안을 비운다. COMPLETED이면 대시보드로 이동한다. 조회만으로 기존 시도나 회원 정보를 삭제하지 않는다. 기존 공통 /api/users/me/onboarding의 유학생 동작은 변경하지 않는다.")
    @ApiResponse(responseCode = "200", description = "사업주 완료 상태", content = @Content(schema = @Schema(implementation = EmployerOnboardingStatusResponse.class)))
    public ResponseEntity<EmployerOnboardingStatusResponse> status(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.status(id(jwt)));
    }

    @PostMapping("/restart")
    @Operation(summary = "사업주 온보딩 새로 시작", description = "미완료 사업주가 온보딩에 진입·재진입할 때 호출한다. 이전 시도·OCR·사업자 인증을 폐기하고 새 attemptId를 반환한다. 계정·정식 약관·전화번호 인증·기존 프로필 데이터는 삭제하지 않고 소셜 연결도 해제하지 않는다. 새로운 시도는 현재 로그인에 연결되며 30분 유효하다. 새 로그인에서는 이전 시도를 사용할 수 없다. 이름·유입 경로·사업장 초안은 서버에 저장하지 않으며 마지막에 함께 제출한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "새 시도", content = @Content(schema = @Schema(implementation = EmployerOnboardingAttemptResponse.class))),
        @ApiResponse(responseCode = "409", description = "이미 온보딩 완료 또는 전화번호 미인증", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EmployerOnboardingAttemptResponse> restart(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.restart(id(jwt), jwt.getClaimAsString("sid")));
    }

    @PostMapping(value = "/business-registration/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "온보딩 사업자등록증 OCR", description = "PDF·JPG·PNG 한 개, 최대 5MB. businessRegistrationFile 파트만 받는다. 사업자번호만 추출하고 주소는 별도로 입력한다. 원본은 보관하지 않는다. 번호를 읽지 못하면 422로 재업로드를 안내한다. 성공 이력은 현재 시도에 저장한다. 재업로드는 이전 OCR·사업자 인증을 무효화하며 OCR 실패 시에도 이전 성공을 재사용하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사업자번호 추출", content = @Content(schema = @Schema(implementation = EmployerOnboardingOcrResponse.class))),
        @ApiResponse(responseCode = "409", description = "전화번호 미인증, 완료된 온보딩 또는 시도 만료·불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "파일 크기 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "415", description = "지원하지 않는 파일 형식", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "사업자번호 인식 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "OCR 서비스 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EmployerOnboardingOcrResponse> recognize(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "restart 응답의 attemptId", required = true) @RequestHeader("X-Onboarding-Attempt") UUID attempt,
            @Parameter(description = "사업자등록증 PDF·JPG·PNG, 최대 5MB", schema = @Schema(type = "string", format = "binary"))
            @RequestPart("businessRegistrationFile") MultipartFile file,
            @Parameter(hidden = true) MultipartHttpServletRequest request) {
        if (request.getMultiFileMap().values().stream().mapToInt(files -> files.size()).sum() != 1
                || !request.getParameterMap().isEmpty()) {
            throw new InvalidWorkplaceException("사업자등록증 파일 하나만 첨부해주세요.");
        }
        return ok(service.recognize(id(jwt), jwt.getClaimAsString("sid"), attempt, file));
    }

    @PostMapping(value = "/business-registration/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "온보딩 사업자번호 상태 확인", description = "현재 시도의 등록증 OCR 성공 후, 사용자가 확인·수정한 번호로 국세청 상태 조회를 수행한다. 진위 확인 API는 호출하지 않는다. 계속사업이면 valid=true와 인증 이력을 저장한다. 휴업·폐업·미등록은 valid=false다. 실패·불일치 결과는 이전 인증을 무효화한다. 인증 후 번호를 수정하면 이 API로 다시 인증해야 한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사업자 상태", content = @Content(schema = @Schema(implementation = BusinessRegistrationVerifyResponse.class))),
        @ApiResponse(responseCode = "409", description = "OCR 미완료, 전화번호 미인증, 완료 또는 시도 만료·불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "국세청 조회 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BusinessRegistrationVerifyResponse> verify(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "restart 응답의 attemptId", required = true) @RequestHeader("X-Onboarding-Attempt") UUID attempt,
            @Valid @RequestBody BusinessRegistrationVerifyRequest request) {
        return ok(service.verifyBusiness(id(jwt), jwt.getClaimAsString("sid"), attempt, request.getBusinessRegistrationNumber()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "사업주 온보딩 최종 저장", description = "현재 로그인·시도·전화번호 인증·등록증 OCR·최종 사업자번호 인증을 검사하고 국세청 상태를 다시 조회한다. 프로필(이름·유입 경로), 사업장, 완료 시각을 한 트랜잭션으로 저장한다. 실패 시 기존 계정·전화번호·약관은 유지하고 이번 프로필·사업장 변경은 모두 롤백한다. 성공한 시도는 소모되며 재제출은 409다. 기존 부분 등록 프로필은 성공 시 갱신한다. 기존 완료 회원은 다시 온보딩하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "온보딩 완료", content = @Content(schema = @Schema(implementation = EmployerOnboardingCompleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "입력 오류 또는 계속사업 상태가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 업종", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "중복 사업자번호, 선행 인증 누락, 시도 만료·불일치 또는 이미 완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "국세청 조회 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EmployerOnboardingCompleteResponse> complete(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "restart 응답의 attemptId", required = true) @RequestHeader("X-Onboarding-Attempt") UUID attempt,
            @Valid @RequestBody EmployerOnboardingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.complete(id(jwt), jwt.getClaimAsString("sid"), attempt, request));
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body); }
}
