package com.hirelink.global.exception;

public class BusinessRegistrationLookupException extends RuntimeException {

	public BusinessRegistrationLookupException() {
		super("사업자등록 상태를 조회하지 못했습니다. 잠시 후 다시 시도해주세요.");
	}
}
