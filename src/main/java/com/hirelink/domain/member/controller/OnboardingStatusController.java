package com.hirelink.domain.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.member.dto.OnboardingStatusResponse;
import com.hirelink.domain.member.service.OnboardingStatusService;
import com.hirelink.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Onboarding Status", description = "로그인 회원 본인의 온보딩 상태 조회")
@RestController
@RequestMapping("/api/users/me/onboarding")
public class OnboardingStatusController {

	private final OnboardingStatusService service;

	public OnboardingStatusController(OnboardingStatusService service) { this.service = service; }

	@Operation(summary = "내 온보딩 상태 조회", description = "계정 생성 이후 로그인 액세스 토큰으로 호출한다. 가입 진행 쿠키만으로는 조회할 수 없다. 서버 회원 유형과 회원 연결 등록증 인증 기록으로 새 유학생 온보딩 적용 여부를 판단한다. 대상 회원의 완료 시각이 없으면 REQUIRED를 반환한다. 과거에 생년월일이나 일부 프로필이 저장되어 있어도 BASIC_INFORMATION으로 항상 생년월일부터 다시 입력한다. 임시 입력값과 마지막 작성 단계는 복원하지 않는다. 프로필이나 일부 입력이 있어도 완료로 추정하지 않는다. 최초 완료 시각이 있으면 COMPLETED와 NONE을 반환한다. 등록증 인증 기록이 없는 LOCAL · 기존 가입 회원과 사업주는 NOT_APPLICABLE과 EXISTING_FLOW로 기존 흐름을 사용하며, 이를 기존 온보딩 완료나 서비스 이용 허가로 해석하지 않는다. 체류기간이 만료되어도 입력 완료 상태는 유지하며 추천 이용 자격은 별도다. 조회는 계정 · 프로필 · 인증 결과 · 완료 시각을 생성하거나 수정하지 않으며 외부 인증을 재호출하지 않는다. 로그인 · 가입 응답과 별도로 이 상태를 확인한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "본인 온보딩 상태", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OnboardingStatusResponse.class), examples = {
			@ExampleObject(name = "required", value = "{\"userId\":1,\"role\":\"STUDENT\",\"status\":\"REQUIRED\",\"nextStep\":\"BASIC_INFORMATION\",\"completedAt\":null}"),
			@ExampleObject(name = "completed", value = "{\"userId\":1,\"role\":\"STUDENT\",\"status\":\"COMPLETED\",\"nextStep\":\"NONE\",\"completedAt\":\"2026-11-20T03:00:00Z\"}"),
			@ExampleObject(name = "existingFlow", value = "{\"userId\":2,\"role\":\"EMPLOYER\",\"status\":\"NOT_APPLICABLE\",\"nextStep\":\"EXISTING_FLOW\",\"completedAt\":null}") })),
		@ApiResponse(responseCode = "401", description = "유효한 로그인 없음 · 계정 생성 전 가입 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}"))),
		@ApiResponse(responseCode = "403", description = "탈퇴한 계정", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}")))
	})
	@GetMapping
	public ResponseEntity<OnboardingStatusResponse> getStatus(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(service.getStatus(Long.valueOf(jwt.getSubject())));
	}
}
