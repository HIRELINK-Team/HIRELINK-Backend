package com.hirelink.global.exception;

public class EmploymentOfferNotFoundException extends RuntimeException {

	public EmploymentOfferNotFoundException() {
		super("채용 제안을 찾을 수 없습니다.");
	}
}
