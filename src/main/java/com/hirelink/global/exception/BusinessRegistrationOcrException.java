package com.hirelink.global.exception;

public class BusinessRegistrationOcrException extends RuntimeException {

	public BusinessRegistrationOcrException() {
		super("사업자등록증 인식 서비스를 이용할 수 없습니다.");
	}
}
