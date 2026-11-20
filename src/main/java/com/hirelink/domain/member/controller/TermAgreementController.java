package com.hirelink.domain.member.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.member.dto.TermAgreementRequest;
import com.hirelink.domain.member.dto.LocationConsentResponse;
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

	@Operation(summary = "내 위치 약관 동의 상태 조회", description = "계정 생성 후 온보딩과 마이페이지에서 같은 로그인 회원의 동의를 조회한다. GPS를 사용하려 할 때 isAgreed=false이면 프론트가 위치 약관 동의를 받고, 반환된 termTypeId와 isAgreed=true를 기존 PUT /api/users/me/term-agreements에 보낸다. true이면 약관 동의창을 반복 표시하지 않는다. 주소 직접 입력은 동의 없이 진행하며 위치 약관은 계정 생성 전 필수 가입 약관 목록에 포함되지 않는다. 미저장은 false/agreedAt=null이며 조회로 동의 기록을 생성하거나 시각을 변경하지 않는다. 약관 본문·번역·기기 위치 권한·GPS 실행은 프론트에서 처리하며 이 API는 좌표를 받거나 저장하지 않는다. 유학생·사업주·LOCAL 회원이 본인의 동의만 조회하며 온보딩 완료나 등록증 인증을 요구하지 않는다. 가입 진행 쿠키만으로는 호출할 수 없다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "본인의 위치 약관 동의 상태", content = @Content(mediaType = "application/json", schema = @Schema(implementation = LocationConsentResponse.class), examples = {
			@ExampleObject(name = "notAgreed", value = "{\"termTypeId\":9,\"termTypeName\":\"위치기반 서비스 이용약관\",\"isAgreed\":false,\"agreedAt\":null}"),
			@ExampleObject(name = "agreed", value = "{\"termTypeId\":9,\"termTypeName\":\"위치기반 서비스 이용약관\",\"isAgreed\":true,\"agreedAt\":\"2026-11-20T05:00:00Z\"}") })),
		@ApiResponse(responseCode = "401", description = "유효한 로그인 없음 또는 회원 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"UNAUTHORIZED\",\"message\":\"유효한 인증 정보가 필요합니다.\"}"))),
		@ApiResponse(responseCode = "403", description = "탈퇴한 회원", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}"))),
		@ApiResponse(responseCode = "404", description = "위치 약관 항목 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"TERM_TYPE_NOT_FOUND\",\"message\":\"존재하지 않는 약관 유형입니다.\"}"))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "/location", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LocationConsentResponse> getLocationConsent(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(termAgreementService.getLocationConsent(Long.valueOf(jwt.getSubject())));
	}

	@Operation(summary = "약관 동의 저장",
			description = "로그인 회원의 요청에 포함된 약관만 저장·갱신하고 해당 목록을 요청 순서로 반환한다. 미전송 약관은 유지한다. true를 다시 보내면 기존 동의 시각을 유지하고 false이면 agreedAt=null, false에서 true이면 현재 시각을 기록한다. 빈 목록은 허용한다. 중복 ID는 400, 없는 약관 ID는 404이며 요청 전체가 함께 반영된다. 필수 동의·역할별 제한은 검사하지 않는다. 위치 약관은 GET /api/users/me/term-agreements/location에서 받은 termTypeId로 저장한다. 온보딩·마이페이지가 같은 기록을 사용하며 GPS 이용 전 동의창 표시와 기기 권한 처리는 프론트가 담당한다.")
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
