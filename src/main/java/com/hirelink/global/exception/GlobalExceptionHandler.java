package com.hirelink.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(EmploymentMatchNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleEmploymentMatchNotFound(EmploymentMatchNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("EMPLOYMENT_MATCH_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(ApplicationCapacityExceededException.class)
	public ResponseEntity<ErrorResponse> handleApplicationCapacityExceeded(
			ApplicationCapacityExceededException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("APPLICATION_CAPACITY_EXCEEDED", exception.getMessage()));
	}

	@ExceptionHandler(DuplicateApplicationException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateApplication(DuplicateApplicationException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("DUPLICATE_APPLICATION", exception.getMessage()));
	}

	@ExceptionHandler(RecommendationStateConflictException.class)
	public ResponseEntity<ErrorResponse> handleRecommendationStateConflict(
			RecommendationStateConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("RECOMMENDATION_STATE_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler(RecommendationNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleRecommendationNotFound(RecommendationNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("RECOMMENDATION_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(InvalidApplicationException.class)
	public ResponseEntity<ErrorResponse> handleInvalidApplication(InvalidApplicationException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
	}

	@ExceptionHandler(EmploymentOfferStateConflictException.class)
	public ResponseEntity<ErrorResponse> handleEmploymentOfferStateConflict(EmploymentOfferStateConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("EMPLOYMENT_OFFER_STATE_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler(EmploymentOfferNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleEmploymentOfferNotFound(EmploymentOfferNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("EMPLOYMENT_OFFER_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(ApplicationStateConflictException.class)
	public ResponseEntity<ErrorResponse> handleApplicationStateConflict(ApplicationStateConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("APPLICATION_STATE_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler(ApplicationNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleApplicationNotFound(ApplicationNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("APPLICATION_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(DuplicateBusinessRegistrationException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateBusinessRegistration(DuplicateBusinessRegistrationException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("DUPLICATE_BUSINESS_REGISTRATION_NUMBER", exception.getMessage()));
	}

	@ExceptionHandler(BusinessRegistrationOcrException.class)
	public ResponseEntity<ErrorResponse> handleBusinessRegistrationOcr(BusinessRegistrationOcrException exception) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ErrorResponse("BUSINESS_REGISTRATION_OCR_FAILED", exception.getMessage()));
	}

	@ExceptionHandler(UnreadableBusinessRegistrationException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableBusinessRegistration(UnreadableBusinessRegistrationException exception) {
		return ResponseEntity.status(422)
				.body(new ErrorResponse("UNREADABLE_BUSINESS_REGISTRATION", exception.getMessage()));
	}

	@ExceptionHandler(UnsupportedBusinessRegistrationFileException.class)
	public ResponseEntity<ErrorResponse> handleUnsupportedBusinessRegistrationFile(UnsupportedBusinessRegistrationFileException exception) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(new ErrorResponse("UNSUPPORTED_FILE_TYPE", exception.getMessage()));
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ErrorResponse> handleInvalidMultipart(MultipartException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", "파일 전송 형식이 올바르지 않습니다."));
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return new ResponseEntity<>(new ErrorResponse("FILE_TOO_LARGE", "사업자등록증 파일은 5MB 이하여야 합니다."), headers, status);
	}

	@ExceptionHandler(JobPostingStateConflictException.class)
	public ResponseEntity<ErrorResponse> handleJobPostingStateConflict(JobPostingStateConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("JOB_POSTING_STATE_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler(JobPostingNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleJobPostingNotFound(JobPostingNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("JOB_POSTING_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(InvalidJobPostingException.class)
	public ResponseEntity<ErrorResponse> handleInvalidJobPosting(InvalidJobPostingException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
	}

	@ExceptionHandler(InvalidWorkplaceException.class)
	public ResponseEntity<ErrorResponse> handleInvalidWorkplace(InvalidWorkplaceException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
	}

	@ExceptionHandler(BusinessTypeNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleBusinessTypeNotFound(BusinessTypeNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("BUSINESS_TYPE_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(WorkplaceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleWorkplaceNotFound(WorkplaceNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("WORKPLACE_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(BusinessRegistrationLookupException.class)
	public ResponseEntity<ErrorResponse> handleBusinessRegistrationLookup(BusinessRegistrationLookupException exception) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ErrorResponse("BUSINESS_REGISTRATION_LOOKUP_FAILED", exception.getMessage()));
	}

	@ExceptionHandler(InvalidMatchingPreferenceException.class)
	public ResponseEntity<ErrorResponse> handleInvalidMatchingPreference(InvalidMatchingPreferenceException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
	}

	@ExceptionHandler(MatchingReferenceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleMatchingReferenceNotFound(MatchingReferenceNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("MATCHING_REFERENCE_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(StudentCareerNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleStudentCareerNotFound(StudentCareerNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("STUDENT_CAREER_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(InvalidStudentCareerException.class)
	public ResponseEntity<ErrorResponse> handleInvalidStudentCareer(InvalidStudentCareerException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
	}

	@ExceptionHandler(StudentProfileNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleStudentProfileNotFound(StudentProfileNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("STUDENT_PROFILE_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(DuplicateStudentProfileException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateStudentProfile(DuplicateStudentProfileException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("DUPLICATE_STUDENT_PROFILE", exception.getMessage()));
	}

	@ExceptionHandler(PhoneNotVerifiedException.class)
	public ResponseEntity<ErrorResponse> handlePhoneNotVerified(PhoneNotVerifiedException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("PHONE_NOT_VERIFIED", exception.getMessage()));
	}

	@ExceptionHandler(DuplicateEmployerProfileException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateEmployerProfile(DuplicateEmployerProfileException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("DUPLICATE_EMPLOYER_PROFILE", exception.getMessage()));
	}

	@ExceptionHandler(EmployerProfileNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleEmployerProfileNotFound(EmployerProfileNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("EMPLOYER_PROFILE_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(InvalidStudentProfileException.class)
	public ResponseEntity<ErrorResponse> handleInvalidStudentProfile(InvalidStudentProfileException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
	}

	@ExceptionHandler(InvalidEmployerProfileException.class)
	public ResponseEntity<ErrorResponse> handleInvalidEmployerProfile(InvalidEmployerProfileException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("FORBIDDEN", "접근 권한이 없습니다."));
	}

	@ExceptionHandler(TermTypeNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleTermTypeNotFound(TermTypeNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("TERM_TYPE_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(DuplicateTermTypeException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateTermType(DuplicateTermTypeException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_INPUT", exception.getMessage()));
	}

	@ExceptionHandler(InvalidPhoneVerificationCodeException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPhoneVerificationCode(InvalidPhoneVerificationCodeException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_VERIFICATION_CODE", exception.getMessage()));
	}

	@ExceptionHandler(DuplicatePhoneNumberException.class)
	public ResponseEntity<ErrorResponse> handleDuplicatePhoneNumber(DuplicatePhoneNumberException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("DUPLICATE_PHONE_NUMBER", exception.getMessage()));
	}

	@ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleMissingAccount(AuthenticationCredentialsNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("UNAUTHORIZED", "로그인이 필요합니다."));
	}

	@ExceptionHandler(PhoneVerificationRateLimitException.class)
	public ResponseEntity<ErrorResponse> handlePhoneVerificationRateLimit(PhoneVerificationRateLimitException exception) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("PHONE_VERIFICATION_RATE_LIMIT", exception.getMessage()));
	}

	@ExceptionHandler(SmsDeliveryException.class)
	public ResponseEntity<ErrorResponse> handleSmsDelivery(SmsDeliveryException exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("SMS_DELIVERY_FAILED", exception.getMessage()));
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("INVALID_REFRESH_TOKEN", exception.getMessage()));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("INVALID_CREDENTIALS", exception.getMessage()));
	}

	@ExceptionHandler(WithdrawnAccountException.class)
	public ResponseEntity<ErrorResponse> handleWithdrawnAccount(WithdrawnAccountException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("WITHDRAWN_ACCOUNT", exception.getMessage()));
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("DUPLICATE_EMAIL", exception.getMessage()));
	}

	@ExceptionHandler(InvalidPasswordException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_PASSWORD", exception.getMessage()));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getDefaultMessage())
				.findFirst()
				.orElse("입력값이 올바르지 않습니다.");
		return new ResponseEntity<>(new ErrorResponse("INVALID_INPUT", message), headers, status);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("요청 처리 중 서버 오류가 발생했습니다.", exception);
			return new ResponseEntity<>(new ErrorResponse("INTERNAL_SERVER_ERROR",
					"서버 오류가 발생했습니다."), headers, status);
		}
		String code = status.value() == 400 ? "INVALID_INPUT" : "REQUEST_ERROR";
		return new ResponseEntity<>(new ErrorResponse(code, "요청 형식이 올바르지 않습니다."), headers, status);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
		log.error("요청 처리 중 예상하지 못한 오류가 발생했습니다.", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
	}
}
