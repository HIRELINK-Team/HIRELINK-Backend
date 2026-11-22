package com.hirelink.domain.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.hirelink.domain.auth.dto.RegistrationFrontResponse;
import com.hirelink.domain.auth.dto.RegistrationVerificationResponse;
import com.hirelink.domain.auth.service.SocialSignupRegistrationService;
import com.hirelink.global.exception.ErrorResponse;
import com.hirelink.global.exception.SocialAuthException;
import com.hirelink.global.security.SocialLoginCookie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth/social/registration")
@Tag(name = "Social Auth")
@SecurityRequirements

@ApiResponses({
	@ApiResponse(responseCode = "400", description = "파일 누락·추가 폼·빈 이미지 또는 필수 약관 미동의", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
		@ExampleObject(name = "INVALID_INPUT", value = "{\"code\":\"INVALID_INPUT\",\"message\":\"등록증 이미지 또는 체류기간 증빙 파일 하나만 첨부해주세요.\"}"),
		@ExampleObject(name = "REQUIRED_TERMS_NOT_AGREED", value = "{\"code\":\"REQUIRED_TERMS_NOT_AGREED\",\"message\":\"고유식별정보 동의를 포함한 필수 약관에 모두 동의해주세요.\"}") })),
	@ApiResponse(responseCode = "401", description = "가입 쿠키 누락·무효·만료 (외부 처리 중 만료 포함)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "INVALID_SOCIAL_SESSION", value = "{\"code\":\"INVALID_SOCIAL_SESSION\",\"message\":\"소셜 인증을 다시 진행해주세요.\"}"))),
	@ApiResponse(responseCode = "403", description = "유학생이 아님, CSRF 오류, 회원 유형 불일치 또는 탈퇴 계정", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
		@ExampleObject(name = "FORBIDDEN", value = "{\"code\":\"FORBIDDEN\",\"message\":\"유학생 가입 중에만 등록증을 확인할 수 있습니다.\"}"),
		@ExampleObject(name = "INVALID_CSRF_TOKEN", value = "{\"code\":\"INVALID_CSRF_TOKEN\",\"message\":\"CSRF 보안 확인값이 없거나 올바르지 않습니다.\"}"),
		@ExampleObject(name = "SOCIAL_ROLE_MISMATCH", value = "{\"code\":\"SOCIAL_ROLE_MISMATCH\",\"message\":\"가입한 회원 유형으로 로그인해주세요.\"}"),
		@ExampleObject(name = "WITHDRAWN_ACCOUNT", value = "{\"code\":\"WITHDRAWN_ACCOUNT\",\"message\":\"탈퇴한 계정입니다.\"}") })),

	@ApiResponse(responseCode = "413", description = "파일 5MB 또는 전체 요청 6MB 초과", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "FILE_TOO_LARGE", value = "{\"code\":\"FILE_TOO_LARGE\",\"message\":\"등록증 이미지 또는 체류기간 증빙 파일은 5MB 이하여야 합니다.\"}"))),


	@ApiResponse(responseCode = "502", description = "OCR 또는 APICK 연결·응답·판정 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
		@ExampleObject(name = "REGISTRATION_OCR_FAILED", value = "{\"code\":\"REGISTRATION_OCR_FAILED\",\"message\":\"등록증 인식 서비스를 이용할 수 없습니다.\"}"),
		@ExampleObject(name = "REGISTRATION_VERIFICATION_FAILED", value = "{\"code\":\"REGISTRATION_VERIFICATION_FAILED\",\"message\":\"등록증 진위 확인을 완료하지 못했습니다. 잠시 후 다시 시도해주세요.\"}") })),
	@ApiResponse(responseCode = "503", description = "기능 비활성 또는 암호화 키·APICK 키·별도 동의 설정 미비", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "REGISTRATION_VERIFICATION_UNAVAILABLE", value = "{\"code\":\"REGISTRATION_VERIFICATION_UNAVAILABLE\",\"message\":\"등록증 확인 서비스를 이용할 수 없습니다.\"}"))),
	@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "INTERNAL_SERVER_ERROR", value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")))
})
public class SocialSignupRegistrationController {

	private final SocialSignupRegistrationService service;
	public SocialSignupRegistrationController(SocialSignupRegistrationService service) { this.service = service; }

	@Parameters({
		@Parameter(name = "socialSignup", in = ParameterIn.COOKIE, required = true, description = "소셜 인증 후 설정한 HttpOnly 가입 쿠키", schema = @Schema(type = "string")),
		@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답 token", schema = @Schema(type = "string")),
		@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	})
	@Operation(summary = "유학생 가입 등록증 앞면 인식", description = "유학생 소셜 가입 세션과 별도 고유식별정보 동의를 포함한 필수 약관 동의가 필요하다. 카메라 촬영 또는 앨범에서 선택한 이미지를 같은 API의 registrationFile에 JPG·PNG 한 개(5MB 이하)로 보낸다. 추가 파일·폼 필드와 OCR 값 직접 수정은 허용하지 않는다. 서버는 파일의 실제 촬영 출처를 증명하지 않는다. 정상 형식의 이미지로 인식을 시작하면 이전 앞면·뒷면·진위·PDF 결과를 먼저 무효화하며 인식 실패에도 이전 결과는 복구하지 않는다. 성공 응답의 registrationNumber에 숫자 6자리-7자리 형식의 등록번호 전체를 반환한다. 기존 maskedRegistrationNumber 키는 제거한다. 응답은 no-store이며 프론트는 확인용 원문을 화면 이탈·인증 완료 시 정리한다. 추출값은 암호화 임시 저장하며 원본 이미지는 보관하지 않는다. 가입 세션 30분 만료는 연장되지 않고, 만료 시 사용 불가·정기 정리 시 삭제된다. 회원·JWT·프로필은 생성하지 않는다.")
	@ApiResponses({
	@ApiResponse(responseCode = "200", description = "앞면 인식 성공. 등록번호 전체 확인 후 뒷면 촬영", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationFrontResponse.class), examples = @ExampleObject(name = "success", value = """
				{"name":"TEST STUDENT","nationalityCode":"VN","visaCode":"D-2","registrationNumber":"000101-5000000","issueDate":"2020-01-01","signupExpiresAt":"2026-11-20T10:30:00Z"}
				"""))),
		@ApiResponse(responseCode = "409", description = "앞면 재촬영 필요 또는 이미 가입된 소셜 계정", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "REGISTRATION_FRONT_REQUIRED", value = "{\"code\":\"REGISTRATION_FRONT_REQUIRED\",\"message\":\"등록증 앞면을 먼저 촬영해주세요. 확인 완료 후 재촬영도 앞면부터 진행해주세요.\"}"),
			@ExampleObject(name = "SOCIAL_ACCOUNT_ALREADY_REGISTERED", value = "{\"code\":\"SOCIAL_ACCOUNT_ALREADY_REGISTERED\",\"message\":\"이미 가입한 소셜 계정입니다. 다시 로그인해주세요.\"}") })),
		@ApiResponse(responseCode = "415", description = "JPG·PNG 이외 형식 또는 파일 시그니처 불일치", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "UNSUPPORTED_FILE_TYPE", value = "{\"code\":\"UNSUPPORTED_FILE_TYPE\",\"message\":\"등록증 촬영 이미지는 JPG, PNG만 사용할 수 있습니다.\"}"))),
		@ApiResponse(responseCode = "422", description = "필수 필드 인식 실패·지원하지 않는 체류자격 또는 기재 정보 불일치", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "UNSUPPORTED_VISA", value = "{\"code\":\"UNSUPPORTED_VISA\",\"message\":\"등록증에서 확인한 체류자격이 D-2여야 합니다.\"}"),
			@ExampleObject(name = "UNREADABLE_REGISTRATION", value = "{\"code\":\"UNREADABLE_REGISTRATION\",\"message\":\"등록증 정보를 확인하지 못했습니다. 선명하게 다시 촬영해주세요.\"}"),
			@ExampleObject(name = "REGISTRATION_NOT_VERIFIED", value = "{\"code\":\"REGISTRATION_NOT_VERIFIED\",\"message\":\"등록증 정보가 일치하지 않습니다. 등록증을 다시 촬영해주세요.\"}") }))
	})
	@PostMapping(value = "/front", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<RegistrationFrontResponse> front(
			@Parameter(hidden = true) @CookieValue(name = SocialLoginCookie.NAME, required = false) String token,
			@Parameter(description = "촬영 또는 앨범에서 선택한 등록증 앞면 JPG·PNG 한 개, 최대 5MB", schema = @Schema(type = "string", format = "binary"))
			@RequestPart("registrationFile") MultipartFile file,
			@Parameter(hidden = true) MultipartHttpServletRequest request) {
		validateParts(request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.front(token, file));
	}

	@Parameters({
		@Parameter(name = "socialSignup", in = ParameterIn.COOKIE, required = true, description = "소셜 인증 후 설정한 HttpOnly 가입 쿠키", schema = @Schema(type = "string")),
		@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답 token", schema = @Schema(type = "string")),
		@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	})
	@Operation(summary = "유학생 가입 등록증 뒷면 인식 · 진위 확인", description = "앞면 인식 후 카메라로 촬영하거나 앨범에서 선택한 뒷면 JPG·PNG 한 개를 registrationFile로 보낸다. 추가 파일·폼 필드는 허용하지 않는다. 정상 형식의 이미지로 처리를 시작하면 이전 뒷면·진위 결과를 무효화한다. 최신 체류 만료일과 일련번호를 읽고 서버에 보관한 앞면 값으로 APICK을 호출한다. 2011-01-01 이전 발급 카드만 일련번호 생략이 가능하다. APICK data.result=1만 통과시키며 과금·HTTP 성공만으로 통과시키지 않는다. 기재 정보 일치 확인이며 얼굴 대조·실물 위변조·최신 체류자격 확인을 보장하지 않는다. 한국 날짜 기준 만료일 당일까지 유효하고 지난 경우 requiresStayDocument=true다. 진위 확인 성공 시 발급일자를 제거하고, PDF 확인이 필요하지 않으면 등록번호도 즉시 제거한다. 진위 확인 성공 후 뒷면 재촬영은 앞면부터 한다. 공통 응답의 serialNumber는 카드에서 읽은 11자리 문자열이며 일련번호 없는 구형 카드는 null이다. 허가일자·신고일은 반환하지 않는다. PDF 필요 시 등록번호와 카드 일련번호를 기존 세션 만료까지 암호화 보관하고, PDF가 필요하지 않으면 일련번호는 저장하지 않는다. PDF 필요 시 stay-document API로 확인한다. 활성화된 유학생 휴대전화 가입에서 이 결과와 체류기간을 재검사한다. 이 응답 자체로 계정·온보딩을 완료하지 않는다.")
	@ApiResponses({
	@ApiResponse(responseCode = "200", description = "기재 정보 일치 확인 성공. 공통 응답의 requiresStayDocument로 PDF 필요 여부 판단", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationVerificationResponse.class), examples = {
				@ExampleObject(name = "cardValid", value = """
						{"serialNumber":"00000000001","stayExpiresOn":"2027-07-27","cardVerified":true,"stayDocumentVerified":false,"requiresStayDocument":false,"signupExpiresAt":"2026-11-20T10:30:00Z"}
						"""),
				@ExampleObject(name = "requiresPdf", value = """
						{"serialNumber":"00000000001","stayExpiresOn":"2026-11-19","cardVerified":true,"stayDocumentVerified":false,"requiresStayDocument":true,"signupExpiresAt":"2026-11-20T10:30:00Z"}
						""") })),
		@ApiResponse(responseCode = "409", description = "앞면 재촬영 필요 또는 이미 가입된 소셜 계정", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "REGISTRATION_FRONT_REQUIRED", value = "{\"code\":\"REGISTRATION_FRONT_REQUIRED\",\"message\":\"등록증 앞면을 먼저 촬영해주세요. 확인 완료 후 재촬영도 앞면부터 진행해주세요.\"}"),
			@ExampleObject(name = "SOCIAL_ACCOUNT_ALREADY_REGISTERED", value = "{\"code\":\"SOCIAL_ACCOUNT_ALREADY_REGISTERED\",\"message\":\"이미 가입한 소셜 계정입니다. 다시 로그인해주세요.\"}") })),
		@ApiResponse(responseCode = "415", description = "JPG·PNG 이외 형식 또는 파일 시그니처 불일치", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "UNSUPPORTED_FILE_TYPE", value = "{\"code\":\"UNSUPPORTED_FILE_TYPE\",\"message\":\"등록증 촬영 이미지는 JPG, PNG만 사용할 수 있습니다.\"}"))),
		@ApiResponse(responseCode = "422", description = "필수 필드 인식 실패·지원하지 않는 체류자격 또는 기재 정보 불일치", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "UNSUPPORTED_VISA", value = "{\"code\":\"UNSUPPORTED_VISA\",\"message\":\"등록증에서 확인한 체류자격이 D-2여야 합니다.\"}"),
			@ExampleObject(name = "UNREADABLE_REGISTRATION", value = "{\"code\":\"UNREADABLE_REGISTRATION\",\"message\":\"등록증 정보를 확인하지 못했습니다. 선명하게 다시 촬영해주세요.\"}"),
			@ExampleObject(name = "REGISTRATION_NOT_VERIFIED", value = "{\"code\":\"REGISTRATION_NOT_VERIFIED\",\"message\":\"등록증 정보가 일치하지 않습니다. 등록증을 다시 촬영해주세요.\"}") }))
	})
	@PostMapping(value = "/back", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<RegistrationVerificationResponse> back(
			@Parameter(hidden = true) @CookieValue(name = SocialLoginCookie.NAME, required = false) String token,
			@Parameter(description = "촬영 또는 앨범에서 선택한 등록증 뒷면 JPG·PNG 한 개, 최대 5MB", schema = @Schema(type = "string", format = "binary"))
			@RequestPart("registrationFile") MultipartFile file,
			@Parameter(hidden = true) MultipartHttpServletRequest request) {
		validateParts(request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.back(token, file));
	}

	@Parameters({
		@Parameter(name = "socialSignup", in = ParameterIn.COOKIE, required = true, description = "HttpOnly 가입 쿠키", schema = @Schema(type = "string")),
		@Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "GET /api/auth/csrf 응답 token", schema = @Schema(type = "string")),
		@Parameter(name = "XSRF-TOKEN", in = ParameterIn.COOKIE, required = true, description = "같은 브라우저의 CSRF 쿠키", schema = @Schema(type = "string"))
	})
	@Operation(summary = "유학생 가입 체류기간 PDF 확인", description = "앞면·뒷면 APICK 확인을 완료했고 카드 체류기간이 한국 날짜 기준 지난 경우 외국인등록 사실증명 PDF를 제출한다. stayDocument 파일 한 개, 5MB 이하·최대 3쪽이며 추가 폼 필드를 허용하지 않는다. 대상자 등록번호가 앞면과 같고 현재 체류기간 만료일이 오늘 이후인 경우만 성공한다. 기간 표기는 종료일을 사용하며 주소·체류자격 변경 이력의 날짜는 사용하지 않는다. 모호한 열·페이지·가림 번호는 422로 거절한다. 실패 시 화면의 ‘다시 촬영’은 PDF 재선택·업로드를 뜻한다. 공통 응답의 serialNumber는 앞선 카드 뒷면 값을 사용하고 stayExpiresOn은 PDF로 확인한 만료일이다. cardVerified=true, stayDocumentVerified=true, requiresStayDocument=false다. 허가일자·신고일은 반환하지 않는다. PDF 실패 시 카드 일련번호를 유지하여 PDF만 재시도할 수 있다. 일련번호를 저장하지 않은 이전 버전의 PDF 대기 세션은 REGISTRATION_FRONT_REQUIRED로 앞면부터 다시 진행한다. 원본 PDF는 보관하지 않으며 성공 후 등록번호·발급일자·일련번호를 제거한다. 성공 후 재확인은 앞면부터 진행한다. 기존 30분 세션은 연장하지 않는다. 문서 진위·전자서명·발급기관 조회 검증이 아니며 APICK을 다시 호출하지 않는다. 활성화된 유학생 휴대전화 가입에서 이 결과와 체류기간을 재검사하며 이 응답 자체로 계정·온보딩을 완료하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "동일 등록번호 · 유효한 체류기간 확인. 카드 일련번호와 PDF 만료일을 공통 형식으로 반환", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationVerificationResponse.class), examples = @ExampleObject(name = "pdfVerified", value = """
				{"serialNumber":"00000000001","stayExpiresOn":"2027-07-27","cardVerified":true,"stayDocumentVerified":true,"requiresStayDocument":false,"signupExpiresAt":"2026-11-20T10:30:00Z"}
				"""))),
		@ApiResponse(responseCode = "409", description = "선행 인증 미완료 · PDF 불필요 · 확인 완료 후 재촬영 필요", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "REGISTRATION_BACK_REQUIRED", value = "{\"code\":\"REGISTRATION_BACK_REQUIRED\",\"message\":\"등록증 앞면과 뒷면의 진위 확인을 먼저 완료해주세요.\"}"),
			@ExampleObject(name = "STAY_DOCUMENT_NOT_REQUIRED", value = "{\"code\":\"STAY_DOCUMENT_NOT_REQUIRED\",\"message\":\"카드의 체류기간이 유효하여 PDF 제출이 필요하지 않습니다.\"}"),
			@ExampleObject(name = "REGISTRATION_FRONT_REQUIRED", value = "{\"code\":\"REGISTRATION_FRONT_REQUIRED\",\"message\":\"등록증 앞면부터 다시 촬영해주세요.\"}"),
			@ExampleObject(name = "SOCIAL_ACCOUNT_ALREADY_REGISTERED", value = "{\"code\":\"SOCIAL_ACCOUNT_ALREADY_REGISTERED\",\"message\":\"이미 가입한 소셜 계정입니다. 다시 로그인해주세요.\"}") })),
		@ApiResponse(responseCode = "415", description = "PDF 이외 형식 또는 파일 시그니처 불일치", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = "{\"code\":\"UNSUPPORTED_FILE_TYPE\",\"message\":\"체류기간 증빙은 PDF만 사용할 수 있습니다.\"}"))),
		@ApiResponse(responseCode = "422", description = "인식 불가 · 등록번호 불일치 · 체류기간 만료. PDF 재선택", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = {
			@ExampleObject(name = "UNSUPPORTED_VISA", value = "{\"code\":\"UNSUPPORTED_VISA\",\"message\":\"등록증에서 확인한 체류자격이 D-2여야 합니다.\"}"),
			@ExampleObject(name = "UNREADABLE_STAY_DOCUMENT", value = "{\"code\":\"UNREADABLE_STAY_DOCUMENT\",\"message\":\"체류기간 증빙을 읽을 수 없습니다. 외국인등록 사실증명 PDF를 다시 선택해주세요.\"}"),
			@ExampleObject(name = "STAY_DOCUMENT_MISMATCH", value = "{\"code\":\"STAY_DOCUMENT_MISMATCH\",\"message\":\"등록증과 증빙서류의 등록번호가 일치하지 않습니다. 본인의 PDF를 다시 선택해주세요.\"}"),
			@ExampleObject(name = "STAY_DOCUMENT_EXPIRED", value = "{\"code\":\"STAY_DOCUMENT_EXPIRED\",\"message\":\"증빙서류의 체류기간이 지났습니다. 유효한 PDF를 다시 선택해주세요.\"}") }))
	})
	@PostMapping(value = "/stay-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<RegistrationVerificationResponse> stayDocument(
			@Parameter(hidden = true) @CookieValue(name = SocialLoginCookie.NAME, required = false) String token,
			@Parameter(description = "외국인등록 사실증명 PDF 한 개, 최대 5MB · 3쪽", schema = @Schema(type = "string", format = "binary"))
			@RequestPart("stayDocument") MultipartFile file,
			@Parameter(hidden = true) MultipartHttpServletRequest request) {
		validateParts(request);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.stayDocument(token, file));
	}

	private void validateParts(MultipartHttpServletRequest request) {
		if (request.getMultiFileMap().values().stream().mapToInt(files -> files.size()).sum() != 1
				|| !request.getParameterMap().isEmpty()) {
			throw new SocialAuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "등록증 이미지 또는 체류기간 증빙 파일 하나만 첨부해주세요.");
		}
	}
}
