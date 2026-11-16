package com.hirelink.domain.auth.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.auth.dto.PhoneVerificationRequest;
import com.hirelink.domain.auth.dto.PhoneVerificationRequestResponse;
import com.hirelink.domain.auth.dto.PhoneVerificationConfirmRequest;
import com.hirelink.domain.auth.dto.PhoneVerificationConfirmResponse;
import com.hirelink.domain.auth.service.PhoneVerificationService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Phone Verification", description = "휴대전화 인증번호 요청·확인")
@RestController
@RequestMapping("/api/auth/phone-verifications")
public class PhoneVerificationController {

	private final PhoneVerificationService phoneVerificationService;

	public PhoneVerificationController(PhoneVerificationService phoneVerificationService) {
		this.phoneVerificationService = phoneVerificationService;
	}

	@Operation(summary = "휴대전화 인증번호 요청",
			description = "로그인한 유학생·사업주 모두 사용한다. 국내 010 번호로 6자리 인증번호를 발송하며 유효시간은 300초다. 계정별·번호별로 재전송 간격 60초, 최근 1시간 최대 5회이며 발송 실패도 요청 횟수에 포함한다. 5회 오입력으로 차단된 계정은 남은 유효시간 동안 재요청할 수 없다. 성공적으로 재발송하면 이전 번호는 무효화된다. 인증번호는 응답에 포함하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = PhoneVerificationRequestResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"expiresIn\":300,\"message\":\"인증번호가 전송되었습니다.\"}"))),
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
		@ApiResponse(responseCode = "429", description = "인증 요청 또는 확인 횟수 제한",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "PHONE_VERIFICATION_RATE_LIMIT", value = "{\"code\":\"PHONE_VERIFICATION_RATE_LIMIT\",\"message\":\"인증 요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "인증 문자 발송 실패 / 예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "SMS_DELIVERY_FAILED", value = "{\"code\":\"SMS_DELIVERY_FAILED\",\"message\":\"인증번호를 전송하지 못했습니다.\"}"),
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PhoneVerificationRequestResponse> requestCode(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PhoneVerificationRequest request) {
		phoneVerificationService.requestCode(Long.valueOf(jwt.getSubject()), request.normalizedPhoneNumber());
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new PhoneVerificationRequestResponse(PhoneVerificationService.CODE_TTL.toSeconds(),
						"인증번호가 전송되었습니다."));
	}

	@Operation(summary = "휴대전화 인증번호 확인",
			description = "로그인 회원이 요청한 번호와 인증번호를 확인한다. 성공하면 회원의 전화번호와 인증 시각을 저장하고 인증번호를 삭제한다. 틀린 번호·만료·미요청은 400, 코드 5회 오입력 시 남은 유효시간 동안 429다. 다른 계정이 이미 사용하는 번호는 이 확인 단계에서 409로 처리한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = PhoneVerificationConfirmResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"phoneNumber\":\"010-1234-5678\",\"verifiedAt\":\"2026-11-01T01:00:00Z\",\"message\":\"휴대전화 인증이 완료되었습니다.\"}"))),
		@ApiResponse(responseCode = "400", description = "입력 형식 또는 값 오류 / 인증번호 불일치 또는 만료",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"요청 형식이 올바르지 않습니다.\"}"),
							@ExampleObject(name = "INVALID_VERIFICATION_CODE", value = "{\"code\":\"INVALID_VERIFICATION_CODE\",\"message\":\"인증번호가 일치하지 않거나 만료되었습니다.\"}")
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
		@ApiResponse(responseCode = "409", description = "이미 등록된 전화번호",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "DUPLICATE_PHONE_NUMBER", value = "{\"code\":\"DUPLICATE_PHONE_NUMBER\",\"message\":\"이미 사용 중인 휴대전화 번호입니다.\"}")
						})),
		@ApiResponse(responseCode = "429", description = "인증 요청 또는 확인 횟수 제한",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "PHONE_VERIFICATION_RATE_LIMIT", value = "{\"code\":\"PHONE_VERIFICATION_RATE_LIMIT\",\"message\":\"인증 요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PhoneVerificationConfirmResponse> confirmCode(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PhoneVerificationConfirmRequest request) {
		PhoneVerificationConfirmResponse response = phoneVerificationService.confirmCode(
				Long.valueOf(jwt.getSubject()), request.normalizedPhoneNumber(), request.code());
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
