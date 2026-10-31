package com.hirelink.global.exception;

public class EmploymentOfferStateConflictException extends RuntimeException {

	public EmploymentOfferStateConflictException() {
		super("응답 기한 내 대기 중인 채용 제안만 거절할 수 있습니다.");
	}
}
