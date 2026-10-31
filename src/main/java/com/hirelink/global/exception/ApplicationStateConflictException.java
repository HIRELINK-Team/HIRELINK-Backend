package com.hirelink.global.exception;

public class ApplicationStateConflictException extends RuntimeException {

	public ApplicationStateConflictException() {
		super("사업주 검토 중인 지원만 취소할 수 있습니다.");
	}
}
