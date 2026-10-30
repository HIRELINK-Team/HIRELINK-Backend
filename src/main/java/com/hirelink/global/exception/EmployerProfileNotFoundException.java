package com.hirelink.global.exception;

public class EmployerProfileNotFoundException extends RuntimeException {

	public EmployerProfileNotFoundException() {
		super("사업주 프로필이 아직 등록되지 않았습니다.");
	}
}
