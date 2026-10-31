package com.hirelink.global.exception;

public class DuplicateBusinessRegistrationException extends RuntimeException {

	public DuplicateBusinessRegistrationException() {
		super("이미 등록된 사업자등록번호입니다.");
	}
}
