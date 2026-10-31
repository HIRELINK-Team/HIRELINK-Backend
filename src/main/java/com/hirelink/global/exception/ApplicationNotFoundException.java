package com.hirelink.global.exception;

public class ApplicationNotFoundException extends RuntimeException {

	public ApplicationNotFoundException() {
		super("지원 내역을 찾을 수 없습니다.");
	}
}
