package com.hirelink.domain.employmentoffer.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.employmentoffer.dto.EmploymentOfferAcceptResponse;
import com.hirelink.domain.employmentoffer.dto.EmploymentOfferCreateResponse;
import com.hirelink.domain.employmentoffer.dto.EmploymentOfferRejectResponse;
import com.hirelink.domain.employmentoffer.service.EmploymentOfferService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Employment Offers", description = "사업주 채용 제안과 유학생 수락·거절 API")
public class EmploymentOfferController {

	private final EmploymentOfferService employmentOfferService;

	public EmploymentOfferController(EmploymentOfferService employmentOfferService) {
		this.employmentOfferService = employmentOfferService;
	}

	@PostMapping("/api/applications/{applicationId}/offers")
	@Operation(summary = "채용 제안 생성", description = "사업주가 본인 채용공고의 응답 기한 내 지원자에게 채용 제안을 보냅니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "채용 제안 생성 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmploymentOfferCreateResponse.class))),
			@ApiResponse(responseCode = "400", description = "지원 ID가 양수가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "사업주 계정이 아니거나 본인 공고의 지원이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "지원을 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "응답 기한이 지났거나 제안 가능한 지원 상태가 아니거나 제안이 이미 존재함",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EmploymentOfferCreateResponse> createOffer(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "채용 제안을 생성할 지원 ID", required = true, example = "201")
			@PathVariable @Positive Long applicationId) {
		EmploymentOfferCreateResponse response = employmentOfferService.createOffer(
				Long.valueOf(jwt.getSubject()), applicationId);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/api/offers/{offerId}/accept")
	@Operation(summary = "채용 제안 수락", description = "유학생이 본인의 응답 기한 내 채용 제안을 수락하고 채용 완료 정보를 생성합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "채용 제안 수락 및 채용 완료 생성 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmploymentOfferAcceptResponse.class))),
			@ApiResponse(responseCode = "400", description = "채용 제안 ID가 양수가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 본인의 제안이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "채용 제안 또는 연결된 지원을 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "응답 기한이 지났거나 수락 가능한 제안·지원 상태가 아니거나 이미 채용 완료됨",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EmploymentOfferAcceptResponse> acceptOffer(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "수락할 채용 제안 ID", required = true, example = "401")
			@PathVariable @Positive Long offerId) {
		EmploymentOfferAcceptResponse response = employmentOfferService.acceptOffer(
				Long.valueOf(jwt.getSubject()), offerId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@PatchMapping("/api/offers/{offerId}/reject")
	@Operation(summary = "채용 제안 거절", description = "유학생이 본인의 응답 기한 내 채용 제안을 거절하고 지원을 종료합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "채용 제안 거절 성공",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmploymentOfferRejectResponse.class))),
			@ApiResponse(responseCode = "400", description = "채용 제안 ID가 양수가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "유학생 계정이 아니거나 본인의 제안이 아니거나 탈퇴한 계정",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "채용 제안 또는 연결된 지원을 찾을 수 없음",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "응답 기한이 지났거나 거절 가능한 제안·지원 상태가 아님",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
	})
	public ResponseEntity<EmploymentOfferRejectResponse> rejectOffer(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "거절할 채용 제안 ID", required = true, example = "401")
			@PathVariable @Positive Long offerId) {
		EmploymentOfferRejectResponse response = employmentOfferService.rejectOffer(
				Long.valueOf(jwt.getSubject()), offerId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
