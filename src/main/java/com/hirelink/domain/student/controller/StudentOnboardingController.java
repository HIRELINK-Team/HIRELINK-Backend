package com.hirelink.domain.student.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.dto.StudentOnboardingRequest;
import com.hirelink.domain.student.dto.StudentOnboardingResponse;
import com.hirelink.domain.student.service.StudentOnboardingService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Student Onboarding", description = "유학생 온보딩 일괄 저장 · 완료")
@RestController
@RequestMapping("/api/students/me/onboarding")
public class StudentOnboardingController {

	private final StudentOnboardingService service;

	public StudentOnboardingController(StudentOnboardingService service) { this.service = service; }

	@Operation(summary = "유학생 온보딩 일괄 저장 · 완료", description = "전화번호 인증과 계정 생성 이후 로그인한 유학생이 마지막 마치기에서 호출한다. 회원 연결 등록증 인증 기록이 필수이며 LOCAL 테스트 가입은 기존 개별 API를 사용한다. 기본정보 · 학업과정 · 경력 · 구직조건 · 우선순위를 한 트랜잭션에서 저장하고 최초 완료 시각을 기록한다. 실패하면 모든 변경을 롤백한다. 국적 · 체류 만료일은 서버 인증 기록에서 가져온다. studyProgram은 ASSOCIATE·BACHELOR·MASTER·DOCTORATE·EXCHANGE 중 하나가 필수이며 각각 D-2-1·D-2-2·D-2-3·D-2-4·D-2-6으로 조합한다. 등록증에서는 D-2를 확인하고 세부 코드는 사용자가 선택한 학업과정으로 생성한다. 등록증 체류자격이 D-2가 아니면 400이다. 등록증 원문 코드는 변경하지 않는다. 학교·학년·학적 상태는 요청할 수 없다. 이름과 생년월일은 가입 시 저장된 값을 사용한다. birthDate는 생략하며 전달하면 보존된 값과 같아야 한다. 이전 버전에서 생년월일을 보존하지 않은 인증 회원만 birthDate가 필수다. 가입 이후 체류기간이 만료되어도 온보딩 완료는 허용하지만 만료일 · 인증 시각을 갱신하지 않으며, 완료가 추천 이용 자격을 뜻하지 않는다. 경력 없음은 careers=[]로 전송한다. topikLevel은 null을 포함해 입력할 수 없으며 TOPIK 성적증명서 최종 등록으로만 반영한다. 근무시간은 최소 1개이며 자정을 넘으면 MON 22:00~00:00, TUE 00:00~02:00처럼 나눠 전송한다. 반경은 정수 100~1,000m, 100m 간격으로 제한한다. 미완료 프로필이 있으면 제출 항목을 갱신하고 경력 · 업종 · 시간 · 우선순위 목록을 교체하며, 학교 등 이번 입력 대상이 아닌 기존 정보는 유지한다. 중복 · 동시 완료 요청은 최초 한 건만 성공하고 이후 409다. 화면별 임시 저장 기능이 아니며 중도 이탈하면 거주지역부터 다시 입력한다. 생년월일 미보존 기존 회원은 생년월일부터 입력한다. 완료 후 수정은 기존 개별 API를 사용한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "전체 저장 및 온보딩 완료", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentOnboardingResponse.class), examples = @ExampleObject(value = "{\"studentId\":1,\"completed\":true,\"completedAt\":\"2026-11-20T03:00:00Z\"}"))),
		@ApiResponse(responseCode = "400", description = "필수 입력 누락 · 잘못된 형식 · 경력 기간 · 구직조건 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "유학생 아님 · 탈퇴", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "등록되지 않은 업종 · 우선순위 기준", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "409", description = "전화번호 미인증 · 등록증 인증 기록 없음 · 이미 온보딩 완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "PHONE_NOT_VERIFIED", value = "{\"code\":\"PHONE_NOT_VERIFIED\",\"message\":\"휴대전화 인증을 먼저 완료해주세요.\"}"),
			@ExampleObject(name = "REGISTRATION_VERIFICATION_REQUIRED", value = "{\"code\":\"REGISTRATION_VERIFICATION_REQUIRED\",\"message\":\"등록증 인증을 거친 가입 정보가 필요합니다.\"}"),
			@ExampleObject(name = "ONBOARDING_ALREADY_COMPLETED", value = "{\"code\":\"ONBOARDING_ALREADY_COMPLETED\",\"message\":\"이미 온보딩을 완료했습니다. 개별 수정 API를 이용해주세요.\"}") }))
	})
	@PostMapping
	public ResponseEntity<StudentOnboardingResponse> complete(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody StudentOnboardingRequest request) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(service.complete(Long.valueOf(jwt.getSubject()), request));
	}
}
