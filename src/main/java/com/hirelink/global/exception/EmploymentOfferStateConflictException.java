package com.hirelink.global.exception;

public class EmploymentOfferStateConflictException extends RuntimeException {

	public EmploymentOfferStateConflictException() {
		super("현재 상태에서 채용 제안을 처리할 수 없습니다.");
	}
}
