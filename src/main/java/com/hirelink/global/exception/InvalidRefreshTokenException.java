package com.hirelink.global.exception;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("유효한 Refresh Token이 필요합니다.");
	}
}
