package com.hirelink.domain.employmentnotification.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.employmentnotification.dto.EmploymentPopupNotificationConfirmResponse;
import com.hirelink.domain.employmentnotification.dto.EmploymentPopupNotificationListResponse;
import com.hirelink.domain.employmentnotification.service.EmploymentPopupNotificationService;
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
@RequestMapping("/api/notifications/popups")
@Tag(name = "Employment Popup Notifications", description = "회원별 채용 1회성 팝업 알림 API")
public class EmploymentPopupNotificationController {

	private final EmploymentPopupNotificationService notificationService;

	public EmploymentPopupNotificationController(EmploymentPopupNotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	@Operation(summary = "미확인 채용 팝업 조회",
			description = "로그인 계정의 미확인 채용 팝업을 오래된 순서로 모두 조회합니다. 확인된 팝업은 다른 기기에서도 다시 반환하지 않습니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "미확인 팝업 조회 성공. 없으면 notifications는 빈 배열",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = EmploymentPopupNotificationListResponse.class),
						examples = @ExampleObject(value = "{\"notifications\":[{\"notificationId\":401,\"type\":\"OFFER_CREATED\",\"message\":\"새로운 채용 제안이 도착했습니다. 서비스에서 응답해 주세요.\",\"applicationId\":201,\"employmentOfferId\":301,\"createdAt\":\"2026-11-20T05:00:00Z\"}]}"))),
		@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "탈퇴한 계정",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EmploymentPopupNotificationListResponse> getUnconfirmed(
			@AuthenticationPrincipal Jwt jwt) {
		EmploymentPopupNotificationListResponse response = notificationService.getUnconfirmed(
				Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/{notificationId}/confirm")
	@Operation(summary = "채용 팝업 확인",
			description = "로그인 계정 소유의 팝업을 확인 처리합니다. 이미 확인한 팝업은 최초 확인 시각을 그대로 반환합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "팝업 확인 성공 또는 이미 확인한 팝업 재확인",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = EmploymentPopupNotificationConfirmResponse.class),
						examples = @ExampleObject(value = "{\"notificationId\":401,\"confirmedAt\":\"2026-11-20T05:05:00Z\"}"))),
		@ApiResponse(responseCode = "400", description = "알림 ID가 양수가 아님",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "다른 회원의 알림이거나 탈퇴한 계정",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "팝업 알림을 찾을 수 없음",
				content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
						schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EmploymentPopupNotificationConfirmResponse> confirm(
			@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "확인할 팝업 알림 ID", required = true, example = "401")
			@PathVariable @Positive Long notificationId) {
		EmploymentPopupNotificationConfirmResponse response = notificationService.confirm(
				Long.valueOf(jwt.getSubject()), notificationId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
