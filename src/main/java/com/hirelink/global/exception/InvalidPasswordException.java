package com.hirelink.global.exception;

public class InvalidPasswordException extends RuntimeException {
	public InvalidPasswordException() {
		super("비밀번호를 처리할 수 없습니다.");
	}
}
