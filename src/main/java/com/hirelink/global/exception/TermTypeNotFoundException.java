package com.hirelink.global.exception;

public class TermTypeNotFoundException extends RuntimeException {
	public TermTypeNotFoundException() {
		super("존재하지 않는 약관 유형입니다.");
	}
}
