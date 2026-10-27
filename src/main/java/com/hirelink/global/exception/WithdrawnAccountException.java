package com.hirelink.global.exception;

public class WithdrawnAccountException extends RuntimeException {
	public WithdrawnAccountException() {
		super("탈퇴한 계정입니다.");
	}
}
