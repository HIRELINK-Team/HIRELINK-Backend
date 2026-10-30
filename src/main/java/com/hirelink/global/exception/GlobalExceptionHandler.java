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
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

	@ExceptionHandler(InvalidStudentProfileException.class)
	public ResponseEntity<ErrorResponse> handleInvalidStudentProfile(InvalidStudentProfileException exception) {
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
