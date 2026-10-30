package com.hirelink.global.exception;

public class DuplicateEmployerProfileException extends RuntimeException {

	public DuplicateEmployerProfileException() {
		super("이미 사업주 프로필이 존재합니다.");
	}
}
