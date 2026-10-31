package com.hirelink.global.exception;

public class DuplicateApplicationException extends RuntimeException {

	public DuplicateApplicationException() {
		super("이미 지원한 채용공고에는 다시 지원할 수 없습니다.");
	}
}
