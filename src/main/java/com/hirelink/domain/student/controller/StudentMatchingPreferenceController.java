package com.hirelink.domain.student.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.dto.StudentMatchingPreferenceResponse;
import com.hirelink.domain.student.dto.StudentMatchingPreferenceUpdateRequest;
import com.hirelink.domain.student.service.StudentMatchingPreferenceService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Student Matching Preferences", description = "유학생 본인 매칭 조건 관리")
@RestController
@RequestMapping("/api/students/me/matching-preferences")
public class StudentMatchingPreferenceController {

	private final StudentMatchingPreferenceService studentMatchingPreferenceService;

	public StudentMatchingPreferenceController(StudentMatchingPreferenceService studentMatchingPreferenceService) {
		this.studentMatchingPreferenceService = studentMatchingPreferenceService;
	}

	@Operation(summary = "내 매칭 조건 조회",
			description = "프로필이 등록된 유학생 본인의 조건을 조회한다. 조건 미입력 시 businessTypeFlexible=false, 목록=[], preferredLocation=null을 반환하며 조회로 데이터를 생성하지 않는다. 업종은 ID 순, 시간은 월~일·시작 시간 순, 우선순위는 1순위부터 반환한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentMatchingPreferenceResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"otherJobName\":null}],\"preferredWorkTimes\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"13:00\"}],\"preferredLocation\":{\"locationName\":\"서울특별시 성동구\",\"latitude\":37.5665,\"longitude\":126.978,\"radiusM\":500},\"priorities\":[{\"criterionId\":1,\"criterionName\":\"WORK_TIME\",\"priorityOrder\":1},{\"criterionId\":2,\"criterionName\":\"WAGE\",\"priorityOrder\":2},{\"criterionId\":3,\"criterionName\":\"DISTANCE\",\"priorityOrder\":3},{\"criterionId\":4,\"criterionName\":\"BUSINESS_TYPE\",\"priorityOrder\":4}]}"))),
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
		@ApiResponse(responseCode = "404", description = "유학생 프로필 미등록",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "STUDENT_PROFILE_NOT_FOUND", value = "{\"code\":\"STUDENT_PROFILE_NOT_FOUND\",\"message\":\"유학생 프로필이 아직 등록되지 않았습니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@GetMapping
	public ResponseEntity<StudentMatchingPreferenceResponse> getPreferences(@AuthenticationPrincipal Jwt jwt) {
		StudentMatchingPreferenceResponse response = studentMatchingPreferenceService.getPreferences(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "매칭 조건 저장·수정",
			description = "수정 반영하기에서 호출하며 상세 화면의 수정은 클라이언트에 임시 보관한다. 보내지 않은 필드는 유지한다. 빈 요청과 수정 대상이 아닌 필드는 400이다. 목록을 보내면 해당 목록 전체를 교체한다. businessTypeFlexible과 preferredJobs는 함께 보낸다. true이면 업종은 [], false이면 1개 이상이고 OTHER는 이름이 필수다. 근무시간 빈 목록은 저장할 수 없다. 최소 희망 시급은 null을 포함해 입력받지 않고 조회에도 포함하지 않는다. 기존 DB 시급 값은 보존한다. 지역은 null로 삭제할 수 없다. 반경은 정수 100~1,000m, 100m 간격으로 제한한다. 위치를 생략하면 기존 반경을 그대로 유지한다. 우선순위는 네 기준·1~4순위를 중복 없이 모두 전송한다. 근무시간은 30분 단위이며 연속 1시간 이상인 구간이 최소 하나 필요하다. 인접한 30분 구간은 연속으로 판단하지만 떨어진 30분 구간의 합계만으로는 허용하지 않는다. 조건 충족 시 별도 30분 구간도 추가할 수 있다. 시작과 종료가 같거나 실제 시간이 겹치면 400이다. 종료가 시작보다 이르면 다음 날 종료이며 시작 요일 하나로 저장·조회한다. 일요일 밤과 월요일 새벽의 겹침도 검사한다. 실패하면 이번 변경 전체를 취소한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentMatchingPreferenceResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"businessTypeFlexible\":false,\"preferredJobs\":[{\"businessTypeId\":1,\"businessTypeName\":\"FOOD_CAFE\",\"otherJobName\":null}],\"preferredWorkTimes\":[{\"dayOfWeek\":\"MON\",\"startTime\":\"09:00\",\"endTime\":\"13:00\"}],\"preferredLocation\":{\"locationName\":\"서울특별시 성동구\",\"latitude\":37.5665,\"longitude\":126.978,\"radiusM\":500},\"priorities\":[{\"criterionId\":1,\"criterionName\":\"WORK_TIME\",\"priorityOrder\":1},{\"criterionId\":2,\"criterionName\":\"WAGE\",\"priorityOrder\":2},{\"criterionId\":3,\"criterionName\":\"DISTANCE\",\"priorityOrder\":3},{\"criterionId\":4,\"criterionName\":\"BUSINESS_TYPE\",\"priorityOrder\":4}]}"))),
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
		@ApiResponse(responseCode = "404", description = "유학생 프로필 미등록 / 업종 또는 우선순위 기준 없음",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "STUDENT_PROFILE_NOT_FOUND", value = "{\"code\":\"STUDENT_PROFILE_NOT_FOUND\",\"message\":\"유학생 프로필이 아직 등록되지 않았습니다.\"}"),
							@ExampleObject(name = "MATCHING_REFERENCE_NOT_FOUND", value = "{\"code\":\"MATCHING_REFERENCE_NOT_FOUND\",\"message\":\"존재하지 않는 업종 또는 우선순위 기준입니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<StudentMatchingPreferenceResponse> updatePreferences(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody StudentMatchingPreferenceUpdateRequest request) {
		StudentMatchingPreferenceResponse response = studentMatchingPreferenceService.updatePreferences(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
