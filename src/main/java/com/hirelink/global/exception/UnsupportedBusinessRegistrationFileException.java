package com.hirelink.global.exception;

public class UnsupportedBusinessRegistrationFileException extends RuntimeException {

	public UnsupportedBusinessRegistrationFileException() {
		super("사업자등록증은 PDF, JPG, PNG 파일만 사용할 수 있습니다.");
	}
}
