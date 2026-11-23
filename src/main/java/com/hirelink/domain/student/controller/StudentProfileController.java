package com.hirelink.domain.student.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hirelink.domain.student.dto.StudentProfileCreateRequest;
import com.hirelink.domain.student.dto.StudentProfileCreateResponse;
import com.hirelink.domain.student.dto.StudentProfileResponse;
import com.hirelink.domain.student.dto.StudentProfileUpdateRequest;
import com.hirelink.domain.student.dto.StudentProfileUpdateResponse;
import com.hirelink.domain.student.service.StudentProfileService;
import com.hirelink.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Student Profiles", description = "유학생 본인 프로필 관리")
@RestController
@RequestMapping("/api/students/me/profile")
public class StudentProfileController {

	private final StudentProfileService studentProfileService;

	public StudentProfileController(StudentProfileService studentProfileService) {
		this.studentProfileService = studentProfileService;
	}

	@Operation(summary = "내 유학생 프로필 조회",
			description = "로그인한 유학생 본인의 프로필과 경력을 조회한다. 체류자격은 D-2, D-4 상위 코드와 기존 세부 코드를 저장된 값 그대로 반환한다. 학업 상태 등 등록되지 않은 선택 정보는 null이다. 경력은 시작월 내림차순 · 같으면 ID 내림차순이며 없으면 []다. 사진 미등록은 null, 등록된 사진 URL 발급 실패는 500이다. 조회 중 프로필을 만들지 않는다. 이 응답은 온보딩 전체 완료 판정이 아니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentProfileResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"studentId\":1,\"name\":\"김하늘\",\"email\":\"student@example.com\",\"phoneNumber\":\"010-1234-5678\",\"birthDate\":\"2000-05-14\",\"nationality\":\"VN\",\"currentRegion\":\"서울특별시 성동구\",\"koreanLevel\":\"INTERMEDIATE\",\"visaCode\":null,\"universityName\":null,\"enrollmentStartDate\":null,\"studyProgramId\":null,\"studyProgram\":null,\"gradeLevel\":null,\"academicState\":null,\"topikLevel\":null,\"stayStartDate\":null,\"stayExpirationDate\":null,\"profileImageUrl\":null,\"hirelinkBadgeIssuedAt\":null,\"careers\":[]}"))),
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
	public ResponseEntity<StudentProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
		StudentProfileResponse response = studentProfileService.getProfile(Long.valueOf(jwt.getSubject()));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "유학생 프로필 등록",
			description = "휴대전화 인증을 완료한 유학생이 본인 프로필을 최초 등록한다. 이름 · 생년월일 · 국적 · 거주지역 · 한국어 수준이 필수다. 한국 기준 만 18세 이상, 국적은 유효한 두 자리 국가 코드여야 한다. 전화번호는 이미 인증된 회원 정보에서 가져온다. 이름 저장과 프로필 생성은 하나의 트랜잭션이며 요청 성공 시 즉시 저장된다. 임시 저장이나 온보딩 전체 일괄 저장 API가 아니며, 성공 응답은 온보딩 전체 완료를 의미하지 않는다. 체류자격 · 학업 상태는 최초 등록 항목이 아니며 이후 프로필 수정 API에서 처리한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentProfileCreateResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"studentId\":1,\"name\":\"김하늘\",\"phoneNumber\":\"010-1234-5678\",\"birthDate\":\"2000-05-14\",\"nationality\":\"VN\",\"currentRegion\":\"서울특별시 성동구\",\"koreanLevel\":\"INTERMEDIATE\"}"))),
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
		@ApiResponse(responseCode = "409", description = "휴대전화 미인증 / 이미 등록된 유학생 프로필",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "PHONE_NOT_VERIFIED", value = "{\"code\":\"PHONE_NOT_VERIFIED\",\"message\":\"휴대전화 인증을 먼저 완료해주세요.\"}"),
							@ExampleObject(name = "DUPLICATE_STUDENT_PROFILE", value = "{\"code\":\"DUPLICATE_STUDENT_PROFILE\",\"message\":\"이미 유학생 프로필이 존재합니다.\"}")
						})),
		@ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
						examples = {
							@ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
						}))
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<StudentProfileCreateResponse> createProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody StudentProfileCreateRequest request) {
		StudentProfileCreateResponse response = studentProfileService.createProfile(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}

	@Operation(summary = "내 유학생 프로필 수정",
			description = "보내지 않은 필드는 유지한다. 빈 요청과 수정 대상이 아닌 필드는 400이다. topikLevel은 null을 포함해 직접 수정할 수 없으며 TOPIK 성적증명서 최종 등록으로만 변경한다. 다른 필드의 null도 400이다. universityName·gradeLevel·academicState·studyProgram·studyProgramId는 입력받지 않고 기존 값을 보존한다. 학업과정은 온보딩에서 선택한다. 등록증 인증 회원의 visaCode 직접 수정은 400이다. 인증 기록이 없는 개발용 프로필만 D-2-1·D-2-2·D-2-3·D-2-4·D-2-6을 입력할 수 있으며 저장된 학업과정과 다르면 400이다. 과거 체류 만료일도 허용하되 기존 시작일보다 앞설 수 없다. 사진 URL은 수정 응답에 포함하지 않는다. 요청 성공 시 즉시 저장되며 온보딩 전체 일괄 저장 API가 아니다. 성공 응답은 온보딩 전체 완료를 의미하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentProfileUpdateResponse.class),
						examples = @ExampleObject(name = "success", value = "{\"studentId\":1,\"name\":\"김하늘\",\"email\":\"student@example.com\",\"phoneNumber\":\"010-1234-5678\",\"birthDate\":\"2000-05-14\",\"nationality\":\"VN\",\"currentRegion\":\"서울특별시 성동구\",\"koreanLevel\":\"INTERMEDIATE\",\"visaCode\":null,\"universityName\":null,\"studyProgramId\":null,\"studyProgram\":null,\"gradeLevel\":null,\"academicState\":null,\"topikLevel\":null,\"stayExpirationDate\":null,\"hirelinkBadgeIssuedAt\":null}"))),
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
	@PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<StudentProfileUpdateResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody StudentProfileUpdateRequest request) {
		StudentProfileUpdateResponse response = studentProfileService.updateProfile(Long.valueOf(jwt.getSubject()), request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
	}
}
