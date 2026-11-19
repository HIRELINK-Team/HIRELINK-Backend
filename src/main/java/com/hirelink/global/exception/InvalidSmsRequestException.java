package com.hirelink.global.exception;

public class InvalidSmsRequestException extends RuntimeException {
	public InvalidSmsRequestException(String message) {
		super(message);
	}
}
