package com.hirelink.global.exception;

public class DuplicateTermTypeException extends RuntimeException {
	public DuplicateTermTypeException() {
		super("요청에 같은 약관 ID가 중복되어 있습니다.");
	}
}
