package com.hirelink.global.exception;

public class BusinessTypeNotFoundException extends RuntimeException {

	public BusinessTypeNotFoundException() {
		super("업종 정보를 찾을 수 없습니다.");
	}
}
