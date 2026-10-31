package com.hirelink.global.exception;

public class EmploymentMatchNotFoundException extends RuntimeException {

	public EmploymentMatchNotFoundException() {
		super("채용완료 정보를 찾을 수 없습니다.");
	}
}
