package com.hirelink.domain.jobposting.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.jobposting.dto.JobPostingCloseResponse;
import com.hirelink.domain.jobposting.dto.JobPostingCreateRequest;
import com.hirelink.domain.jobposting.dto.JobPostingCreateResponse;
import com.hirelink.domain.jobposting.dto.JobPostingDetailResponse;
import com.hirelink.domain.jobposting.dto.JobPostingListResponse;
import com.hirelink.domain.jobposting.dto.JobPostingUpdateRequest;
import com.hirelink.domain.jobposting.dto.JobPostingUpdateResponse;
import com.hirelink.domain.jobposting.service.JobPostingService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Job Postings", description = "채용공고 등록·조회·수정·마감")
@RestController
public class JobPostingController {

	private final JobPostingService jobPostingService;

	public JobPostingController(JobPostingService jobPostingService) {
		this.jobPostingService = jobPostingService;
	}

	@Operation(summary = "채용공고 등록·임시저장",
			description = "사업주가 본인 사업장에 새 공고를 생성한다. DRAFT는 workplaceId·status만 필수다. RECRUITING은 모든 근무조건과 근무시간이 필수이며 두 날짜는 한국 기준 오늘부터 허용한다. 임시저장은 과거 날짜도 저장한다. 제목은 사업장명 + 직무 + 모집으로 생성하며 직무 미입력은 null이다. 근무시간은 30분 단위이며 같은 요일의 겹침·중복·동일 시작/종료는 400, 인접한 구간은 허용한다. 종료 00:00은 그 요일의 자정이며 밤을 넘는 근무는 두 요일로 나눠 보낸다. 공고와 시간 목록은 함께 저장된다.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobPostingCreateRequest.class),
							examples = {
									@ExampleObject(name = "DRAFT", summary = "최소 입력으로 임시저장",
											value = "{\"workplaceId\":1,\"status\":\"DRAFT\"}"),
									@ExampleObject(name = "RECRUITING", summary = "모든 필수 조건을 입력하여 모집 시작",
											value = """
													{
													  "workplaceId": 1, "status": "RECRUITING", "businessTypeId": 1,
													  "jobRole": "바리스타", "recruitmentCount": 2,
													  "wageType": "HOURLY", "wageAmount": 13000,
													  "workStartDate": "2027-03-01", "applicationDeadlineDate": "2027-03-14",
													  "requiredKoreanLevel": "INTERMEDIATE", "preferredExperience": "NONE",
													  "workTimeNegotiable": false,
													  "workTimes": [{"dayOfWeek": "MON", "startTime": "09:00", "endTime": "13:00"}]
													}
													""")
							})))
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobPostingCreateResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"jobPostingId\":1,\"workplaceId\":1,\"workplaceName\":\"예시카페\",\"title\":\"예시카페 바리스타 모집\",\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"jobRole\":\"바리스타\",\"recruitmentCount\":2,\"wageType\":\"HOURLY\",\"wageAmount\":13000,\"workStartDate\":\"2027-03-01\",\"requiredKoreanLevel\":\"INTERMEDIATE\",\"preferredExperience\":\"NONE\",\"applicationDeadlineDate\":\"2027-03-14\",\"workTimeNegotiable\":false,\"status\":\"RECRUITING\",\"workTimes\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"13:00\"}],\"createdAt\":\"2026-11-01T01:00:00Z\"}"))),
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
		@ApiResponse(responseCode = "404", description = "사업장 없음 / 업종 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "WORKPLACE_NOT_FOUND", value = "{\"code\":\"WORKPLACE_NOT_FOUND\",\"message\":\"사업장 정보를 찾을 수 없습니다.\"}"),
							@ExampleObject(name = "BUSINESS_TYPE_NOT_FOUND", value = "{\"code\":\"BUSINESS_TYPE_NOT_FOUND\",\"message\":\"업종 정보를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping(value = "/api/job-postings", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JobPostingCreateResponse> createJobPosting(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody JobPostingCreateRequest request) {
		JobPostingCreateResponse response = jobPostingService.createJobPosting(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "내 채용공고 목록 조회",
			description = "사업주 본인 사업장의 공고를 생성 시각 내림차순·같으면 ID 내림차순으로 반환한다. 기본은 RECRUITING·CLOSED, includeDraft=true이면 DRAFT도 포함한다. status를 지정하면 includeDraft보다 우선한다. status=DRAFT와 잘못된 필터값은 400이다. 임시저장 미입력은 null, 근무시간은 []다. 페이지네이션과 조회 중 자동 마감은 없다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobPostingListResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"jobPostings\":[{\"jobPostingId\":1,\"workplaceId\":1,\"workplaceName\":\"예시카페\",\"jobRole\":\"바리스타\",\"wageType\":\"HOURLY\",\"wageAmount\":13000,\"status\":\"RECRUITING\",\"applicationDeadlineDate\":\"2027-03-14\",\"workTimes\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"13:00\"}],\"createdAt\":\"2026-11-01T01:00:00Z\"}]}"))),
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
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@GetMapping("/api/employers/me/job-postings")
	public ResponseEntity<JobPostingListResponse> getMyJobPostings(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "공고 상태 필터. 지정 시 includeDraft보다 우선", schema = @Schema(allowableValues = {"RECRUITING", "CLOSED"}))
			@RequestParam(required = false) String status, @Parameter(description = "status 생략 시 임시저장 포함 여부", schema = @Schema(type = "string", allowableValues = {"true", "false"}, defaultValue = "false"))
			@RequestParam(required = false) String includeDraft) {
		JobPostingListResponse response = jobPostingService.getMyJobPostings(Long.valueOf(jwt.getSubject()), status, includeDraft);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "채용공고 수정",
			description = "보내지 않은 필드는 유지한다. 빈 요청과 수정 대상이 아닌 필드는 400이다. 본인 공고만 수정 가능하며 null과 사업장 변경은 불가하다. 근무시간은 전체 교체하며 [] 삭제는 DRAFT에서만 가능하다. DRAFT→RECRUITING은 저장값과 입력값을 합쳐 모든 필수 조건과 두 날짜를 검사한다. 모집 중 수정은 이번에 보낸 날짜만 한국 기준 오늘 이상인지 검사한다. DRAFT는 과거 날짜 허용이다. 모집 중→DRAFT 또는 CLOSED 수정·재모집은 409다. 제목을 재생성하며 실패 시 요청 전체를 취소한다. 근무시간은 30분 단위이며 같은 요일의 겹침·중복·동일 시작/종료는 400, 인접한 구간은 허용한다. 종료 00:00은 그 요일의 자정이며 밤을 넘는 근무는 두 요일로 나눠 보낸다. ")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobPostingUpdateResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"jobPostingId\":1,\"workplaceId\":1,\"workplaceName\":\"예시카페\",\"title\":\"예시카페 바리스타 모집\",\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"jobRole\":\"바리스타\",\"recruitmentCount\":2,\"wageType\":\"HOURLY\",\"wageAmount\":13000,\"workStartDate\":\"2027-03-01\",\"requiredKoreanLevel\":\"INTERMEDIATE\",\"preferredExperience\":\"NONE\",\"applicationDeadlineDate\":\"2027-03-14\",\"workTimeNegotiable\":false,\"status\":\"RECRUITING\",\"workTimes\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"13:00\"}],\"updatedAt\":\"2026-11-01T01:00:00Z\"}"))),
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
		@ApiResponse(responseCode = "404", description = "채용공고 없음 / 업종 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "JOB_POSTING_NOT_FOUND", value = "{\"code\":\"JOB_POSTING_NOT_FOUND\",\"message\":\"채용공고를 찾을 수 없습니다.\"}"),
							@ExampleObject(name = "BUSINESS_TYPE_NOT_FOUND", value = "{\"code\":\"BUSINESS_TYPE_NOT_FOUND\",\"message\":\"업종 정보를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "409", description = "현재 공고 상태에서 처리 불가",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "JOB_POSTING_STATE_CONFLICT", value = "{\"code\":\"JOB_POSTING_STATE_CONFLICT\",\"message\":\"모집 중인 공고만 마감할 수 있습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PatchMapping(value = "/api/job-postings/{jobPostingId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JobPostingUpdateResponse> updateJobPosting(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "채용공고 ID (Long 정수)", example = "1") @PathVariable Long jobPostingId, @Valid @RequestBody JobPostingUpdateRequest request) {
		JobPostingUpdateResponse response = jobPostingService.updateJobPosting(Long.valueOf(jwt.getSubject()), jobPostingId, request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "채용공고 모집 마감",
			description = "요청 본문 없이 사업주 본인의 RECRUITING 공고를 마감한다. 상태를 CLOSED로 바꾸고 closedAt·updatedAt을 기록한다. 해당 공고의 진행 중 지원(WAITING_RESPONSE·OFFERED)과 연결된 대기 제안(PENDING)도 공고 마감 사유로 종료한다. 공고 내용·근무시간·기존 채용 완료 기록은 보존한다. 임시저장·이미 마감 상태는 409며 재모집은 지원하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobPostingCloseResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"jobPostingId\":1,\"status\":\"CLOSED\",\"closedAt\":\"2026-11-01T01:00:00Z\"}"))),
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
		@ApiResponse(responseCode = "404", description = "채용공고 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "JOB_POSTING_NOT_FOUND", value = "{\"code\":\"JOB_POSTING_NOT_FOUND\",\"message\":\"채용공고를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "409", description = "현재 공고 상태에서 처리 불가",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "JOB_POSTING_STATE_CONFLICT", value = "{\"code\":\"JOB_POSTING_STATE_CONFLICT\",\"message\":\"모집 중인 공고만 마감할 수 있습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PatchMapping("/api/job-postings/{jobPostingId}/close")
	public ResponseEntity<JobPostingCloseResponse> closeJobPosting(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "채용공고 ID (Long 정수)", example = "1") @PathVariable Long jobPostingId) {
		JobPostingCloseResponse response = jobPostingService.closeJobPosting(Long.valueOf(jwt.getSubject()), jobPostingId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "채용공고 상세 조회",
			description = "사업주는 본인 공고의 모든 상태를, 유학생은 RECRUITING 공고만 조회할 수 있다. 임시저장 미입력은 null, 근무시간은 []다. 업종은 공고에서 선택한 값이며 사업장 업종으로 자동 대체하지 않는다. 시간 목록은 월~일·시작 시간 순으로 반환한다. 저장된 제목을 반환하며 조회 중 자동 마감하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobPostingDetailResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"jobPostingId\":1,\"workplaceId\":1,\"workplaceName\":\"예시카페\",\"title\":\"예시카페 바리스타 모집\",\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"jobRole\":\"바리스타\",\"recruitmentCount\":2,\"wageType\":\"HOURLY\",\"wageAmount\":13000,\"workStartDate\":\"2027-03-01\",\"requiredKoreanLevel\":\"INTERMEDIATE\",\"preferredExperience\":\"NONE\",\"applicationDeadlineDate\":\"2027-03-14\",\"workTimeNegotiable\":false,\"status\":\"RECRUITING\",\"workTimes\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"13:00\"}],\"workplaceAddress\":\"서울특별시 성동구 예시로 10\",\"latitude\":37.5665,\"longitude\":126.978}"))),
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
		@ApiResponse(responseCode = "404", description = "채용공고 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "JOB_POSTING_NOT_FOUND", value = "{\"code\":\"JOB_POSTING_NOT_FOUND\",\"message\":\"채용공고를 찾을 수 없습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@GetMapping("/api/job-postings/{jobPostingId}")
	public ResponseEntity<JobPostingDetailResponse> getJobPosting(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "채용공고 ID (Long 정수)", example = "1") @PathVariable Long jobPostingId) {
		JobPostingDetailResponse response = jobPostingService.getJobPosting(Long.valueOf(jwt.getSubject()), jobPostingId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
