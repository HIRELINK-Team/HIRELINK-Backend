package com.hirelink.domain.member.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.member.dto.TermAgreementRequest;
import com.hirelink.domain.member.dto.TermAgreementResponse;
import com.hirelink.domain.member.service.TermAgreementService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Term Agreements", description = "회원별 약관 동의 관리")
@RestController
@RequestMapping("/api/users/me/term-agreements")
public class TermAgreementController {

	private final TermAgreementService termAgreementService;

	public TermAgreementController(TermAgreementService termAgreementService) {
		this.termAgreementService = termAgreementService;
	}

	@Operation(summary = "약관 동의 저장",
			description = "로그인 회원의 요청에 포함된 약관만 저장·갱신하고 해당 목록을 요청 순서로 반환한다. 미전송 약관은 유지한다. true를 다시 보내면 기존 동의 시각을 유지하고 false이면 agreedAt=null, false에서 true이면 현재 시각을 기록한다. 빈 목록은 허용한다. 중복 ID는 400, 없는 약관 ID는 404이며 요청 전체가 함께 반영된다. 필수 동의·역할별 제한은 검사하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = TermAgreementResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"agreements\":[{\"termTypeId\":1,\"isAgreed\":true,\"agreedAt\":\"2026-11-01T01:00:00Z\"},{\"termTypeId\":3,\"isAgreed\":false,\"agreedAt\":null}]}"))),
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
		@ApiResponse(responseCode = "404", description = "약관 종류 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "TERM_TYPE_NOT_FOUND", value = "{\"code\":\"TERM_TYPE_NOT_FOUND\",\"message\":\"존재하지 않는 약관 유형입니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TermAgreementResponse> saveAgreements(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody TermAgreementRequest request) {
		TermAgreementResponse response = termAgreementService.saveAgreements(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
